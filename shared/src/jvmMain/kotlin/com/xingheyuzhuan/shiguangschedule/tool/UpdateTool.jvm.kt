package com.xingheyuzhuan.shiguangschedule.tool

import java.awt.Desktop
import java.net.URI

actual object PlatformUpdateStrategy {
    actual val isUpdateSupported: Boolean = true
    actual val supportsInAppInstall: Boolean = false

    /**
     * JVM 端直接返回 null，自动退回到 Releases 标签页链接
     */
    actual fun parseTargetUrl(response: ApiReleaseResponse): String? = null

    /**
     * 调用系统默认浏览器打开网页
     */
    actual fun openUrl(url: String) {
        try {
            if (Desktop.isDesktopSupported()) {
                val desktop = Desktop.getDesktop()
                if (desktop.isSupported(Desktop.Action.BROWSE)) {
                    desktop.browse(URI(url))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    actual suspend fun downloadAndInstallApk(
        candidateUrls: List<Pair<String, String>>,
        versionTag: String,
        onProgress: (UpdateDownloadState) -> Unit
    ): Result<Unit> {
        val fallback = candidateUrls.lastOrNull()?.second
        if (!fallback.isNullOrBlank()) openUrl(fallback)
        return Result.success(Unit)
    }
}
