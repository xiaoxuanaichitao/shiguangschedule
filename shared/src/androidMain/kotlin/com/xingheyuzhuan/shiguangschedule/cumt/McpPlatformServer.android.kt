package com.xingheyuzhuan.shiguangschedule.cumt

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

actual class McpPlatformServer actual constructor() {
    actual val isSupported: Boolean = true

    private var serverSocket: ServerSocket? = null
    private var scope: CoroutineScope? = null
    private val sseSessions = ConcurrentHashMap<String, Channel<String>>()

    actual fun start(
        port: Int,
        bindAllInterfaces: Boolean,
        authToken: String,
        onJsonRpc: suspend (String) -> String?
    ): Result<String> {
        stop()
        return runCatching {
            val bindAddress = if (bindAllInterfaces) {
                InetAddress.getByName("0.0.0.0")
            } else {
                InetAddress.getByName("127.0.0.1")
            }
            val socket = ServerSocket(port, 50, bindAddress).apply {
                reuseAddress = true
            }
            serverSocket = socket
            val serverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            scope = serverScope

            serverScope.launch {
                while (isActive && !socket.isClosed) {
                    val client = runCatching { socket.accept() }.getOrNull() ?: break
                    serverScope.launch {
                        handleClient(client, authToken, onJsonRpc)
                    }
                }
            }

            val displayHost = if (bindAllInterfaces) {
                getLocalLanAddresses().firstOrNull() ?: "127.0.0.1"
            } else {
                "127.0.0.1"
            }
            "http://$displayHost:$port/mcp"
        }
    }

    actual fun stop() {
        sseSessions.values.forEach { it.close() }
        sseSessions.clear()
        runCatching { serverSocket?.close() }
        serverSocket = null
        scope?.cancel()
        scope = null
    }

    actual fun getLocalLanAddresses(): List<String> {
        val result = mutableListOf<String>()
        runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (!iface.isUp || iface.isLoopback || iface.isVirtual) continue
                val addrs = iface.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (addr is Inet4Address && !addr.isLoopbackAddress && !addr.isLinkLocalAddress) {
                        addr.hostAddress?.let { result.add(it) }
                    }
                }
            }
        }
        return (result.distinct() + "127.0.0.1").distinct()
    }

    private suspend fun handleClient(
        socket: Socket,
        authToken: String,
        onJsonRpc: suspend (String) -> String?
    ) {
        try {
            socket.soTimeout = 30_000
            val input = BufferedInputStream(socket.getInputStream())
            val output = socket.getOutputStream()

            val requestLine = readAsciiLine(input) ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return
            val method = parts[0].uppercase()
            val rawTarget = parts[1]
            val path = rawTarget.substringBefore('?').trimEnd('/').ifEmpty { "/" }
            val queryParams = parseQueryParams(rawTarget.substringAfter('?', ""))

            val headers = mutableMapOf<String, String>()
            while (true) {
                val line = readAsciiLine(input) ?: break
                if (line.isEmpty()) break
                val colon = line.indexOf(':')
                if (colon > 0) {
                    val key = line.substring(0, colon).trim().lowercase()
                    val value = line.substring(colon + 1).trim()
                    headers[key] = value
                }
            }

            if (method == "OPTIONS") {
                writeHttpResponse(output, 204, "No Content", "text/plain", ByteArray(0))
                return
            }

            if (method == "GET" && (path == "/health" || path == "/")) {
                val body = """{"status":"ok","service":"${ShiguangMcpDispatcher.SERVER_NAME}","version":"${ShiguangMcpDispatcher.SERVER_VERSION}","endpoints":["/mcp","/sse"]}"""
                writeHttpResponse(output, 200, "OK", "application/json; charset=utf-8", body.toByteArray(Charsets.UTF_8))
                return
            }

            if (authToken.isNotBlank()) {
                val headerAuth = headers["authorization"].orEmpty()
                val bearerToken = if (headerAuth.startsWith("Bearer ", ignoreCase = true)) {
                    headerAuth.substring(7).trim()
                } else {
                    ""
                }
                val queryToken = queryParams["token"].orEmpty()
                if (bearerToken != authToken && queryToken != authToken) {
                    val err = """{"jsonrpc":"2.0","id":null,"error":{"code":-32001,"message":"Unauthorized: invalid or missing Bearer token"}}"""
                    writeHttpResponse(output, 401, "Unauthorized", "application/json; charset=utf-8", err.toByteArray(Charsets.UTF_8))
                    return
                }
            }

            when {
                method == "POST" && path == "/mcp" -> {
                    val bodyBytes = readRequestBody(input, headers)
                    val responseJson = onJsonRpc(bodyBytes.toString(Charsets.UTF_8))
                    if (responseJson == null) {
                        writeHttpResponse(output, 202, "Accepted", "application/json; charset=utf-8", ByteArray(0))
                    } else {
                        writeHttpResponse(
                            output,
                            200,
                            "OK",
                            "application/json; charset=utf-8",
                            responseJson.toByteArray(Charsets.UTF_8),
                            extraHeaders = mapOf("Mcp-Session-Id" to (headers["mcp-session-id"] ?: UUID.randomUUID().toString()))
                        )
                    }
                }

                method == "GET" && path == "/sse" -> {
                    handleSseSession(socket, output, authToken)
                }

                method == "POST" && path == "/messages" -> {
                    val sessionId = queryParams["sessionId"].orEmpty()
                    val bodyBytes = readRequestBody(input, headers)
                    val responseJson = onJsonRpc(bodyBytes.toString(Charsets.UTF_8))
                    val channel = sseSessions[sessionId]
                    if (channel != null && responseJson != null) {
                        channel.trySend(responseJson)
                    }
                    writeHttpResponse(
                        output,
                        202,
                        "Accepted",
                        "application/json; charset=utf-8",
                        (responseJson ?: """{"accepted":true}""").toByteArray(Charsets.UTF_8)
                    )
                }

                else -> {
                    val body = """{"error":"Not Found","path":"$path"}"""
                    writeHttpResponse(output, 404, "Not Found", "application/json; charset=utf-8", body.toByteArray(Charsets.UTF_8))
                }
            }
        } catch (_: Exception) {
            // 客户端断开连接
        } finally {
            runCatching { socket.close() }
        }
    }

    private suspend fun handleSseSession(
        socket: Socket,
        output: OutputStream,
        authToken: String
    ) {
        val sessionId = UUID.randomUUID().toString().replace("-", "").take(16)
        val channel = Channel<String>(capacity = 32)
        sseSessions[sessionId] = channel
        socket.soTimeout = 0

        val headerStr = buildString {
            append("HTTP/1.1 200 OK\r\n")
            append("Content-Type: text/event-stream; charset=utf-8\r\n")
            append("Cache-Control: no-cache\r\n")
            append("Connection: keep-alive\r\n")
            append("Access-Control-Allow-Origin: *\r\n")
            append("\r\n")
        }
        output.write(headerStr.toByteArray(Charsets.UTF_8))
        val endpointPath = if (authToken.isNotBlank()) {
            "/messages?sessionId=$sessionId&token=$authToken"
        } else {
            "/messages?sessionId=$sessionId"
        }
        output.write("event: endpoint\ndata: $endpointPath\n\n".toByteArray(Charsets.UTF_8))
        output.flush()

        val pingJob: Job? = scope?.launch {
            while (isActive && !socket.isClosed) {
                delay(20_000)
                runCatching {
                    synchronized(output) {
                        output.write(": keepalive\n\n".toByteArray(Charsets.UTF_8))
                        output.flush()
                    }
                }.onFailure {
                    channel.close()
                }
            }
        }

        try {
            for (msg in channel) {
                synchronized(output) {
                    output.write("event: message\ndata: $msg\n\n".toByteArray(Charsets.UTF_8))
                    output.flush()
                }
            }
        } finally {
            pingJob?.cancel()
            sseSessions.remove(sessionId)
            channel.close()
        }
    }

    private fun writeHttpResponse(
        output: OutputStream,
        code: Int,
        statusText: String,
        contentType: String,
        body: ByteArray,
        extraHeaders: Map<String, String> = emptyMap()
    ) {
        val header = buildString {
            append("HTTP/1.1 $code $statusText\r\n")
            append("Content-Type: $contentType\r\n")
            append("Content-Length: ${body.size}\r\n")
            append("Connection: close\r\n")
            append("Access-Control-Allow-Origin: *\r\n")
            append("Access-Control-Allow-Headers: Authorization, Content-Type, Accept, Mcp-Session-Id\r\n")
            append("Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n")
            extraHeaders.forEach { (k, v) -> append("$k: $v\r\n") }
            append("\r\n")
        }
        output.write(header.toByteArray(Charsets.UTF_8))
        if (body.isNotEmpty()) output.write(body)
        output.flush()
    }

    private fun readAsciiLine(input: BufferedInputStream): String? {
        val out = ByteArrayOutputStream()
        var sawAny = false
        while (true) {
            val b = input.read()
            if (b == -1) return if (sawAny) out.toString("UTF-8") else null
            sawAny = true
            if (b == '\n'.code) break
            if (b != '\r'.code) out.write(b)
        }
        return out.toString("UTF-8")
    }

    private fun readRequestBody(input: BufferedInputStream, headers: Map<String, String>): ByteArray {
        if (headers["transfer-encoding"]?.contains("chunked", ignoreCase = true) == true) {
            val out = ByteArrayOutputStream()
            val maxBytes = 4 * 1024 * 1024
            while (out.size() < maxBytes) {
                val sizeLine = readAsciiLine(input)?.substringBefore(';')?.trim() ?: break
                val chunkSize = sizeLine.toIntOrNull(16) ?: break
                if (chunkSize <= 0) {
                    readAsciiLine(input)
                    break
                }
                val chunk = readExactBytes(input, chunkSize.coerceAtMost(maxBytes - out.size()))
                out.write(chunk)
                readAsciiLine(input)
            }
            return out.toByteArray()
        }
        val contentLength = headers["content-length"]?.toIntOrNull()?.coerceIn(0, 4 * 1024 * 1024) ?: 0
        return readExactBytes(input, contentLength)
    }

    private fun readExactBytes(input: BufferedInputStream, length: Int): ByteArray {
        if (length <= 0) return ByteArray(0)
        val buffer = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val read = input.read(buffer, offset, length - offset)
            if (read <= 0) break
            offset += read
        }
        return if (offset == length) buffer else buffer.copyOf(offset)
    }

    private fun parseQueryParams(query: String): Map<String, String> {
        if (query.isBlank()) return emptyMap()
        return query.split('&').mapNotNull { pair ->
            val idx = pair.indexOf('=')
            if (idx <= 0) return@mapNotNull null
            val k = runCatching { URLDecoder.decode(pair.substring(0, idx), "UTF-8") }.getOrDefault(pair.substring(0, idx))
            val v = runCatching { URLDecoder.decode(pair.substring(idx + 1), "UTF-8") }.getOrDefault(pair.substring(idx + 1))
            k to v
        }.toMap()
    }
}
