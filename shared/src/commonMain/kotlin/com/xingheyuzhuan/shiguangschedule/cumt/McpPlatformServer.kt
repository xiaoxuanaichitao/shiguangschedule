package com.xingheyuzhuan.shiguangschedule.cumt

/**
 * 跨平台轻量本地 MCP HTTP/SSE 嵌入式服务端。
 * Android 与 JVM 端基于标准 ServerSocket + Kotlin 协程实现零外部依赖服务；iOS 端返回优雅降级提示。
 */
expect class McpPlatformServer() {
    val isSupported: Boolean

    fun start(
        port: Int,
        bindAllInterfaces: Boolean,
        authToken: String,
        onJsonRpc: suspend (String) -> String?
    ): Result<String>

    fun stop()

    fun getLocalLanAddresses(): List<String>
}
