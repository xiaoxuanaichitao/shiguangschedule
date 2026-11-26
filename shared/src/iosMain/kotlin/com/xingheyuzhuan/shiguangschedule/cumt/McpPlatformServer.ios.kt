package com.xingheyuzhuan.shiguangschedule.cumt

actual class McpPlatformServer actual constructor() {
    actual val isSupported: Boolean = false

    actual fun start(
        port: Int,
        bindAllInterfaces: Boolean,
        authToken: String,
        onJsonRpc: suspend (String) -> String?
    ): Result<String> {
        return Result.failure(UnsupportedOperationException("iOS 端暂不支持监听本地 MCP 端口，请在 Android 或桌面端开启"))
    }

    actual fun stop() = Unit

    actual fun getLocalLanAddresses(): List<String> = listOf("127.0.0.1")
}
