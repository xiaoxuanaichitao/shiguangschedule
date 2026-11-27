package com.xingheyuzhuan.shiguangschedule.cumt

import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URL
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class McpPlatformServerJvmTest {

    private fun pickFreePort(): Int = ServerSocket(0).use { it.localPort }

    @Test
    fun mcpServerEnforcesAuthAndHandlesJsonRpcAndNotifications() {
        val port = pickFreePort()
        val token = "sg_unit_test_secret"
        val server = McpPlatformServer()

        val startRes = server.start(
            port = port,
            bindAllInterfaces = false,
            authToken = token,
            onJsonRpc = { body ->
                if (body.contains("notifications/initialized")) {
                    null
                } else {
                    """{"jsonrpc":"2.0","id":1,"result":{"echo":true}}"""
                }
            }
        )
        assertTrue(startRes.isSuccess, "MCP server failed to start: ${startRes.exceptionOrNull()?.message}")

        try {
            // 1. /health 无需鉴权
            val healthConn = (URL("http://127.0.0.1:$port/health").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
            }
            assertEquals(200, healthConn.responseCode)
            val healthBody = healthConn.inputStream.bufferedReader().readText()
            assertTrue(healthBody.contains("shiguang-cumt-schedule"))

            // 2. 未携带 Bearer Token 访问 /mcp 必须返回 401
            val unauthConn = (URL("http://127.0.0.1:$port/mcp").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                outputStream.use { it.write("""{"jsonrpc":"2.0","id":1,"method":"ping"}""".toByteArray()) }
            }
            assertEquals(401, unauthConn.responseCode)

            // 3. 携带正确 Bearer Token 访问 /mcp 返回 200 与 JSON-RPC 结果
            val authConn = (URL("http://127.0.0.1:$port/mcp").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer $token")
                outputStream.use { it.write("""{"jsonrpc":"2.0","id":1,"method":"ping"}""".toByteArray()) }
            }
            assertEquals(200, authConn.responseCode)
            val authBody = authConn.inputStream.bufferedReader().readText()
            assertTrue(authBody.contains("\"echo\":true"))

            // 4. JSON-RPC notification 返回 202 Accepted（同时测试末尾斜杠 /mcp/ 兼容）
            val notifyConn = (URL("http://127.0.0.1:$port/mcp/").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer $token")
                outputStream.use { it.write("""{"jsonrpc":"2.0","method":"notifications/initialized"}""".toByteArray()) }
            }
            assertEquals(202, notifyConn.responseCode)

            // 5. Transfer-Encoding: chunked 分块传输请求也能完整读取请求体
            val chunkedConn = (URL("http://127.0.0.1:$port/mcp").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                setChunkedStreamingMode(12)
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer $token")
                outputStream.use { it.write("""{"jsonrpc":"2.0","id":2,"method":"ping"}""".toByteArray()) }
            }
            assertEquals(200, chunkedConn.responseCode)
            assertTrue(chunkedConn.inputStream.bufferedReader().readText().contains("\"echo\":true"))
        } finally {
            server.stop()
        }
    }
}
