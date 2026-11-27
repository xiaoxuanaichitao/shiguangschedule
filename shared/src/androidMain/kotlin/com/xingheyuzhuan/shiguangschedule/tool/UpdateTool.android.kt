package com.xingheyuzhuan.shiguangschedule.tool

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

actual object PlatformUpdateStrategy : KoinComponent {
    actual val isUpdateSupported: Boolean = true
    actual val supportsInAppInstall: Boolean = true

    private val SUPPORTED_ABIS = listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86", "universal")

    private fun getDeviceAbi(): String {
        val deviceAbis = Build.SUPPORTED_ABIS
        val knownSplits = setOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
        return deviceAbis.firstOrNull { it in knownSplits } ?: "universal"
    }

    actual fun parseTargetUrl(response: ApiReleaseResponse): String? {
        val abiUrlMap = mutableMapOf<String, String>()
        var fallbackApkUrl: String? = null

        for (asset in response.assets) {
            val fileName = asset.name.lowercase()
            if (!fileName.endsWith(".apk")) continue
            if (fallbackApkUrl == null) fallbackApkUrl = asset.downloadUrl

            for (abi in SUPPORTED_ABIS) {
                if (fileName.contains("-$abi-") || fileName.endsWith("-$abi.apk") || fileName.contains(abi)) {
                    abiUrlMap[abi] = asset.downloadUrl
                    break
                }
            }
        }

        val deviceAbi = getDeviceAbi()
        return abiUrlMap[deviceAbi]
            ?: abiUrlMap["universal"]
            ?: abiUrlMap.values.firstOrNull()
            ?: fallbackApkUrl
    }

    actual fun openUrl(url: String) {
        try {
            val context: Context = get()
            val intent = Intent(Intent.ACTION_VIEW, url.toUri()).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    actual suspend fun downloadAndInstallApk(
        candidateUrls: List<Pair<String, String>>,
        versionTag: String,
        onProgress: (UpdateDownloadState) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val context: Context = get()
        val updateDir = File(context.cacheDir, "apk_updates").apply {
            if (!exists()) mkdirs()
        }
        // 清理历史残留安装包，释放缓存空间
        updateDir.listFiles()?.forEach { old ->
            if (old.isFile && old.name.endsWith(".apk", ignoreCase = true)) {
                runCatching { old.delete() }
            }
        }

        val safeTag = versionTag.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "latest" }
        val targetFile = File(updateDir, "shiguang_$safeTag.apk")
        var lastError: String = "下载失败，请尝试浏览器下载"

        for ((label, urlStr) in candidateUrls) {
            val ok = tryDownloadSingle(
                urlStr = urlStr,
                sourceLabel = label,
                destFile = targetFile,
                onProgress = onProgress
            )
            if (ok.isSuccess) {
                onProgress(UpdateDownloadState.Installing)
                return@withContext runCatching {
                    installApkFile(context, targetFile)
                }.onFailure { err ->
                    onProgress(UpdateDownloadState.Error("调起安装器失败：${err.message ?: "请尝试浏览器下载"}"))
                }
            } else {
                lastError = ok.exceptionOrNull()?.message ?: lastError
            }
        }

        onProgress(UpdateDownloadState.Error(lastError))
        Result.failure(IllegalStateException(lastError))
    }

    private fun tryDownloadSingle(
        urlStr: String,
        sourceLabel: String,
        destFile: File,
        onProgress: (UpdateDownloadState) -> Unit
    ): Result<Unit> {
        var conn: HttpURLConnection? = null
        return try {
            onProgress(
                UpdateDownloadState.Downloading(
                    progress = 0f,
                    downloadedBytes = 0L,
                    totalBytes = -1L,
                    sourceLabel = sourceLabel
                )
            )
            conn = openFollowingRedirects(urlStr)
            val code = conn.responseCode
            if (code !in 200..299) {
                return Result.failure(IllegalStateException("$sourceLabel 返回 HTTP $code"))
            }
            val totalBytes = conn.contentLengthLong
            val buffer = ByteArray(16 * 1024)
            var downloaded = 0L
            var lastEmitTime = 0L
            var checkedMagic = false

            if (destFile.exists()) destFile.delete()

            conn.inputStream.use { input ->
                FileOutputStream(destFile).use { output ->
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        if (!checkedMagic) {
                            checkedMagic = true
                            // APK 必须是 ZIP 格式（前两字节为 PK = 0x50 0x4B），防止镜像站返回 HTML 错误页
                            if (read < 2 || buffer[0] != 0x50.toByte() || buffer[1] != 0x4B.toByte()) {
                                return Result.failure(IllegalStateException("$sourceLabel 返回了无效的安装包文件"))
                            }
                        }
                        output.write(buffer, 0, read)
                        downloaded += read
                        val now = System.currentTimeMillis()
                        if (now - lastEmitTime >= 120L) {
                            lastEmitTime = now
                            val frac = if (totalBytes > 0L) {
                                (downloaded.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                            } else {
                                -1f
                            }
                            onProgress(
                                UpdateDownloadState.Downloading(
                                    progress = frac,
                                    downloadedBytes = downloaded,
                                    totalBytes = totalBytes,
                                    sourceLabel = sourceLabel
                                )
                            )
                        }
                    }
                    output.flush()
                }
            }

            if (downloaded < 512 * 1024L) {
                destFile.delete()
                return Result.failure(IllegalStateException("安装包体积异常，已自动切换线路"))
            }

            onProgress(
                UpdateDownloadState.Downloading(
                    progress = 1f,
                    downloadedBytes = downloaded,
                    totalBytes = if (totalBytes > 0L) totalBytes else downloaded,
                    sourceLabel = sourceLabel
                )
            )
            Result.success(Unit)
        } catch (e: Exception) {
            runCatching { destFile.delete() }
            Result.failure(e)
        } finally {
            conn?.disconnect()
        }
    }

    private fun openFollowingRedirects(initialUrl: String): HttpURLConnection {
        var currentUrl = initialUrl
        repeat(6) {
            val conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 12_000
                readTimeout = 30_000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "ShiguangSchedule-CUMT")
            }
            val code = conn.responseCode
            if (code in listOf(301, 302, 303, 307, 308)) {
                val loc = conn.getHeaderField("Location")
                conn.disconnect()
                if (loc.isNullOrBlank()) throw IllegalStateException("重定向地址为空")
                currentUrl = URL(URL(currentUrl), loc).toString()
            } else {
                return conn
            }
        }
        throw IllegalStateException("重定向次数过多")
    }

    private fun installApkFile(context: Context, apkFile: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(installIntent)
    }
}
