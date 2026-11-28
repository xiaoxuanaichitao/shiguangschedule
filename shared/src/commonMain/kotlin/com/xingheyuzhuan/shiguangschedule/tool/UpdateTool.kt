package com.xingheyuzhuan.shiguangschedule.tool

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single

enum class UpdatePlatform(val title: String) {
    GITHUB("GitHub 官方")
}

sealed class UpdateStatus {
    data class Found(
        val versionName: String,
        val versionCode: Int = 0,
        val changelog: String,
        val targetUrl: String,
        val isDirectDownload: Boolean,
        val releasePageUrl: String = targetUrl
    ) : UpdateStatus()

    data class Latest(val currentVersion: String) : UpdateStatus()
    data class Error(val message: String) : UpdateStatus()
    data object NotSupported : UpdateStatus()
    data object Checking : UpdateStatus()
    data object Idle : UpdateStatus()
}

sealed interface UpdateDownloadState {
    data object Idle : UpdateDownloadState
    data class Downloading(
        val progress: Float,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val sourceLabel: String = ""
    ) : UpdateDownloadState
    data object Installing : UpdateDownloadState
    data class Error(val message: String) : UpdateDownloadState
}

@Serializable
data class ApiReleaseResponse(
    @SerialName("tag_name") val tagName: String = "",
    @SerialName("body") val body: String = "",
    @SerialName("html_url") val htmlUrl: String = "",
    @SerialName("assets") val assets: List<ApiAsset> = emptyList()
)

@Serializable
data class ApiAsset(
    @SerialName("name") val name: String = "",
    @SerialName("browser_download_url") val downloadUrl: String = ""
)

@Serializable
data class UpdateManifest(
    val versionCode: Int = 0,
    val versionName: String = "",
    val tagName: String = "",
    val changelog: String = "",
    val apkUrl: String = "",
    val fallbackPageUrl: String = ""
)

expect object PlatformUpdateStrategy {
    val isUpdateSupported: Boolean
    val supportsInAppInstall: Boolean
    fun parseTargetUrl(response: ApiReleaseResponse): String?
    fun openUrl(url: String)
    suspend fun downloadAndInstallApk(
        candidateUrls: List<Pair<String, String>>,
        versionTag: String,
        onProgress: (UpdateDownloadState) -> Unit
    ): Result<Unit>
}

/**
 * 添加 @Single 注解，SharedModule 的 @ComponentScan 会自动发现并注册为单例
 */
@Single
class UpdateChecker(
    private val httpClient: HttpClient = defaultHttpClient
) {
    companion object {
        const val GITHUB_REPO = "xiaoxuanaichitao/shiguangschedule"
        private const val MANIFEST_DIRECT_URL =
            "https://github.com/$GITHUB_REPO/releases/latest/download/update-manifest.json"
        private val MIRROR_PREFIXES = listOf(
            "国内加速通道 1" to "https://gh-proxy.com/",
            "国内加速通道 2" to "https://ghfast.top/"
        )

        val defaultHttpClient by lazy {
            HttpClient {
                install(HttpTimeout) {
                    requestTimeoutMillis = 6_500L
                    connectTimeoutMillis = 4_500L
                    socketTimeoutMillis = 6_500L
                }
                install(ContentNegotiation) {
                    json(Json {
                        ignoreUnknownKeys = true
                        coerceInputValues = true
                        isLenient = true
                    })
                }
            }
        }

        private val BUILD_CODE_REGEX = Regex("""(?i)(?:-b|\.b|build)(\d+)""")

        fun parseBuildCodeFromTag(tag: String): Int {
            val match = BUILD_CODE_REGEX.find(tag.trim()) ?: return 0
            return match.groupValues.getOrNull(1)?.toIntOrNull() ?: 0
        }

        fun isNewerRelease(
            latestTag: String,
            latestVersionCode: Int,
            currentVersionName: String,
            currentVersionCode: Int
        ): Boolean {
            val effectiveRemoteCode = if (latestVersionCode > 0) {
                latestVersionCode
            } else {
                parseBuildCodeFromTag(latestTag)
            }
            if (effectiveRemoteCode > 0 && currentVersionCode > 0) {
                if (effectiveRemoteCode > currentVersionCode) return true
                if (effectiveRemoteCode < currentVersionCode) return false
            }
            val latestBase = latestTag
                .removePrefix("v")
                .removePrefix("V")
                .substringBefore('-')
                .trim()
            val currentBase = currentVersionName
                .removePrefix("v")
                .removePrefix("V")
                .substringBefore('-')
                .trim()
            return isNewerVersion(latestBase, currentBase)
        }

        internal fun isNewerVersion(latest: String, current: String): Boolean {
            val latestParts = latest.split('.').mapNotNull { it.toIntOrNull() }
            val currentParts = current.split('.').mapNotNull { it.toIntOrNull() }
            val maxLen = maxOf(latestParts.size, currentParts.size)
            for (i in 0 until maxLen) {
                val l = latestParts.getOrElse(i) { 0 }
                val c = currentParts.getOrElse(i) { 0 }
                if (l > c) return true
                if (l < c) return false
            }
            return false
        }

        fun buildDownloadCandidates(directUrl: String): List<Pair<String, String>> {
            val clean = directUrl.trim()
            if (clean.isEmpty()) return emptyList()
            if (!clean.startsWith("https://github.com/")) {
                return listOf("官方下载" to clean)
            }
            return buildList {
                MIRROR_PREFIXES.forEach { (label, prefix) ->
                    add(label to "$prefix$clean")
                }
                add("GitHub 官方直连" to clean)
            }
        }
    }

    suspend fun checkUpdate(
        platform: UpdatePlatform = UpdatePlatform.GITHUB,
        currentVersionName: String,
        currentVersionCode: Int = 0
    ): UpdateStatus = withContext(Dispatchers.IO) {
        if (!PlatformUpdateStrategy.isUpdateSupported) {
            return@withContext UpdateStatus.NotSupported
        }

        // 多线路并发竞速（国内镜像清单 + 直连清单 + GitHub API），谁最快返回有效结果就立刻弹窗并取消其余慢线路
        val manifestUrls = MIRROR_PREFIXES.map { (_, prefix) -> "$prefix$MANIFEST_DIRECT_URL" } + MANIFEST_DIRECT_URL
        val totalProbes = manifestUrls.size + 1

        coroutineScope {
            val resultChannel = Channel<UpdateStatus?>(capacity = totalProbes)
            val jobs = buildList {
                manifestUrls.forEach { url ->
                    add(launch {
                        val status = runCatching {
                            val manifest = httpClient.get(url) {
                                header("User-Agent", "ShiguangSchedule-CUMT")
                            }.body<UpdateManifest>()
                            manifestToUpdateStatus(manifest, currentVersionName, currentVersionCode)
                        }.getOrNull()
                        resultChannel.send(status)
                    })
                }
                add(launch {
                    val status = runCatching {
                        val apiUrl = when (platform) {
                            UpdatePlatform.GITHUB -> "https://api.github.com/repos/$GITHUB_REPO/releases/latest"
                        }
                        val response = httpClient.get(apiUrl) {
                            header("Accept", "application/vnd.github+json")
                            header("User-Agent", "ShiguangSchedule-CUMT")
                        }.body<ApiReleaseResponse>()
                        releaseApiToUpdateStatus(response, currentVersionName, currentVersionCode)
                    }.getOrNull()
                    resultChannel.send(status)
                })
            }

            var winner: UpdateStatus? = null
            repeat(totalProbes) {
                val res = resultChannel.receive()
                if (res != null && winner == null) {
                    winner = res
                    jobs.forEach { it.cancel() }
                    return@repeat
                }
            }
            jobs.forEach { it.cancel() }
            resultChannel.close()
            winner ?: UpdateStatus.Error("网络请求失败，请检查网络连接后重试")
        }
    }

    private fun manifestToUpdateStatus(
        manifest: UpdateManifest,
        currentVersionName: String,
        currentVersionCode: Int
    ): UpdateStatus? {
        if (manifest.versionCode <= 0 && manifest.tagName.isBlank() && manifest.versionName.isBlank()) {
            return null
        }
        val displayTag = manifest.tagName.ifBlank { "v${manifest.versionName}" }
        val isNewer = isNewerRelease(
            latestTag = displayTag,
            latestVersionCode = manifest.versionCode,
            currentVersionName = currentVersionName,
            currentVersionCode = currentVersionCode
        )
        if (!isNewer) {
            return UpdateStatus.Latest(formatCurrentVersion(currentVersionName, currentVersionCode))
        }
        val pageUrl = manifest.fallbackPageUrl.ifBlank {
            "https://github.com/$GITHUB_REPO/releases/tag/$displayTag"
        }
        val directApkUrl = manifest.apkUrl.trim()
        return UpdateStatus.Found(
            versionName = displayTag,
            versionCode = manifest.versionCode.takeIf { it > 0 } ?: parseBuildCodeFromTag(displayTag),
            changelog = manifest.changelog.ifBlank { "包含最新功能优化与问题修复。" },
            targetUrl = directApkUrl.ifBlank { pageUrl },
            isDirectDownload = directApkUrl.endsWith(".apk", ignoreCase = true),
            releasePageUrl = pageUrl
        )
    }

    private fun releaseApiToUpdateStatus(
        response: ApiReleaseResponse,
        currentVersionName: String,
        currentVersionCode: Int
    ): UpdateStatus? {
        if (response.tagName.isBlank()) return null
        val remoteBuildCode = parseBuildCodeFromTag(response.tagName)
        if (!isNewerRelease(response.tagName, remoteBuildCode, currentVersionName, currentVersionCode)) {
            return UpdateStatus.Latest(formatCurrentVersion(currentVersionName, currentVersionCode))
        }
        val downloadUrl = PlatformUpdateStrategy.parseTargetUrl(response)
        val pageUrl = response.htmlUrl.ifBlank {
            "https://github.com/$GITHUB_REPO/releases/tag/${response.tagName}"
        }
        val (targetUrl, isDirectDownload) = if (!downloadUrl.isNullOrEmpty()) {
            Pair(downloadUrl, true)
        } else {
            Pair(pageUrl, false)
        }
        return UpdateStatus.Found(
            versionName = response.tagName,
            versionCode = remoteBuildCode,
            changelog = response.body.ifBlank { "包含最新功能优化与问题修复。" },
            targetUrl = targetUrl,
            isDirectDownload = isDirectDownload,
            releasePageUrl = pageUrl
        )
    }

    private fun formatCurrentVersion(versionName: String, versionCode: Int): String =
        if (versionCode > 0) "$versionName (Build $versionCode)" else versionName

    suspend fun downloadAndInstall(
        found: UpdateStatus.Found,
        onProgress: (UpdateDownloadState) -> Unit
    ): Result<Unit> {
        if (!PlatformUpdateStrategy.supportsInAppInstall || !found.isDirectDownload) {
            PlatformUpdateStrategy.openUrl(found.targetUrl)
            return Result.success(Unit)
        }
        val candidates = buildDownloadCandidates(found.targetUrl)
        return PlatformUpdateStrategy.downloadAndInstallApk(
            candidateUrls = candidates,
            versionTag = found.versionName,
            onProgress = onProgress
        )
    }

    fun launchUpdate(targetUrl: String) {
        PlatformUpdateStrategy.openUrl(targetUrl)
    }
}
