package com.xingheyuzhuan.shiguangschedule.cumt

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.koin.core.annotation.Single
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * 标准 MCP (Model Context Protocol, JSON-RPC 2.0) 协议分发器。
 * 兼容 Streamable HTTP (2025-03-26)、HTTP+SSE (2024-11-05) 与 Desktop Stdio 模式。
 */
@OptIn(ExperimentalUuidApi::class)
@Single
class ShiguangMcpDispatcher(
    private val toolEngine: ScheduleToolEngine
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val _auditLogs = MutableStateFlow<List<CumtMcpAuditEntry>>(emptyList())
    val auditLogs: StateFlow<List<CumtMcpAuditEntry>> = _auditLogs.asStateFlow()

    fun clearAuditLogs() {
        _auditLogs.value = emptyList()
    }

    /**
     * 处理单条 JSON-RPC 2.0 请求。
     * 若请求为 Notification（无 id 或 notifications/ 前缀），返回 null 表示无需响应体。
     */
    suspend fun handleJsonRpc(
        rawBody: String,
        allowWrite: Boolean
    ): String? {
        val reqObj = runCatching {
            json.parseToJsonElement(rawBody.trim()).jsonObject
        }.getOrElse {
            return buildErrorResponse(JsonNull, -32700, "Parse error: invalid JSON").toString()
        }

        val id: JsonElement? = reqObj["id"]
        val method = reqObj["method"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val params = (reqObj["params"] as? JsonObject) ?: JsonObject(emptyMap())

        if (method.isBlank()) {
            return if (id == null) null else buildErrorResponse(id, -32600, "Invalid Request: missing method").toString()
        }

        // JSON-RPC 通知消息（如 notifications/initialized）不需要返回响应
        if (method.startsWith("notifications/") || id == null) {
            return null
        }

        return when (method) {
            "initialize" -> {
                val clientVersion = params["protocolVersion"]?.jsonPrimitive?.contentOrNull
                    ?.takeIf { it.isNotBlank() } ?: MCP_PROTOCOL_VERSION
                recordAudit(method = method, toolName = null, allowed = true, summary = "外部 Agent 建立 MCP 会话 ($clientVersion)")
                buildSuccessResponse(
                    id = id,
                    result = buildJsonObject {
                        put("protocolVersion", clientVersion)
                        putJsonObject("capabilities") {
                            putJsonObject("tools") { put("listChanged", false) }
                            putJsonObject("resources") { put("subscribe", false); put("listChanged", false) }
                        }
                        putJsonObject("serverInfo") {
                            put("name", SERVER_NAME)
                            put("version", SERVER_VERSION)
                        }
                        put(
                            "instructions",
                            "这是「矿大时光课表」本地 MCP 服务。可读取当前课表、查询空闲节次、查看矿大考试与成绩；开启写入权限后还可新增实验课、调课、修改或删除课程。"
                        )
                    }
                ).toString()
            }

            "ping" -> buildSuccessResponse(id, JsonObject(emptyMap())).toString()

            "tools/list" -> {
                recordAudit(method = method, toolName = null, allowed = true, summary = "列出 ${ScheduleToolEngine.TOOL_DESCRIPTORS.size} 个课表工具")
                buildSuccessResponse(
                    id = id,
                    result = buildJsonObject {
                        put("tools", toolEngine.toMcpToolsJson())
                    }
                ).toString()
            }

            "tools/call" -> {
                val toolName = params["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val args = (params["arguments"] as? JsonObject) ?: JsonObject(emptyMap())
                if (toolName.isBlank()) {
                    return buildErrorResponse(id, -32602, "Invalid params: missing tool name").toString()
                }
                val isWrite = toolEngine.isWriteTool(toolName)
                val exec = toolEngine.executeTool(toolName = toolName, arguments = args, allowWrite = allowWrite)
                recordAudit(
                    method = method,
                    toolName = toolName,
                    allowed = !isWrite || allowWrite,
                    summary = exec.summary
                )
                buildSuccessResponse(
                    id = id,
                    result = buildJsonObject {
                        putJsonArray("content") {
                            addJsonObject {
                                put("type", "text")
                                put("text", exec.payloadJson.toString())
                            }
                        }
                        put("isError", !exec.success)
                    }
                ).toString()
            }

            "resources/list" -> {
                recordAudit(method = method, toolName = null, allowed = true, summary = "列出 ${ScheduleToolEngine.RESOURCE_DESCRIPTORS.size} 个只读资源")
                buildSuccessResponse(
                    id = id,
                    result = buildJsonObject {
                        put("resources", toolEngine.toMcpResourcesJson())
                    }
                ).toString()
            }

            "resources/read" -> {
                val uri = params["uri"]?.jsonPrimitive?.contentOrNull.orEmpty()
                if (uri.isBlank()) {
                    return buildErrorResponse(id, -32602, "Invalid params: missing resource uri").toString()
                }
                val readResult = toolEngine.readResource(uri)
                readResult.fold(
                    onSuccess = { contentText ->
                        recordAudit(method = method, toolName = uri, allowed = true, summary = "读取资源 $uri")
                        buildSuccessResponse(
                            id = id,
                            result = buildJsonObject {
                                putJsonArray("contents") {
                                    addJsonObject {
                                        put("uri", uri)
                                        put("mimeType", "application/json")
                                        put("text", contentText)
                                    }
                                }
                            }
                        ).toString()
                    },
                    onFailure = { err ->
                        recordAudit(method = method, toolName = uri, allowed = false, summary = err.message ?: "读取失败")
                        buildErrorResponse(id, -32602, err.message ?: "Failed to read resource").toString()
                    }
                )
            }

            "prompts/list" -> {
                buildSuccessResponse(
                    id = id,
                    result = buildJsonObject { putJsonArray("prompts") {} }
                ).toString()
            }

            else -> buildErrorResponse(id, -32601, "Method not found: $method").toString()
        }
    }

    private fun recordAudit(
        method: String,
        toolName: String?,
        allowed: Boolean,
        summary: String
    ) {
        val entry = CumtMcpAuditEntry(
            id = Uuid.random().toString().take(8),
            timestampMillis = Clock.System.now().toEpochMilliseconds(),
            method = method,
            toolName = toolName,
            allowed = allowed,
            summary = summary
        )
        _auditLogs.update { list -> (listOf(entry) + list).take(40) }
    }

    private fun buildSuccessResponse(id: JsonElement, result: JsonObject): JsonObject =
        buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", id)
            put("result", result)
        }

    private fun buildErrorResponse(id: JsonElement, code: Int, message: String): JsonObject =
        buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", id)
            putJsonObject("error") {
                put("code", code)
                put("message", message)
            }
        }

    companion object {
        const val MCP_PROTOCOL_VERSION = "2025-03-26"
        const val SERVER_NAME = "shiguang-cumt-schedule"
        const val SERVER_VERSION = "2.1.0"

        fun buildClientConfigSnippet(
            host: String,
            port: Int,
            token: String
        ): String {
            val cleanHost = host.ifBlank { "127.0.0.1" }
            val url = "http://$cleanHost:$port/mcp"
            val headersBlock = if (token.isNotBlank()) {
                """,
      "headers": {
        "Authorization": "Bearer $token"
      }"""
            } else {
                ""
            }
            return """{
  "mcpServers": {
    "shiguang-cumt-schedule": {
      "url": "$url"$headersBlock
    }
  }
}"""
        }
    }
}
