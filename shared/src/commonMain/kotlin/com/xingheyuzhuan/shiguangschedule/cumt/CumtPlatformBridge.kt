package com.xingheyuzhuan.shiguangschedule.cumt

/**
 * 矿大专属功能所需的平台能力桥接（例如 Android Wi-Fi 网络连接状态检测、WebView 会话清理）。
 */
interface CumtPlatformBridge {
    fun isWifiConnected(): Boolean
    fun clearWebCookies() = Unit
}
