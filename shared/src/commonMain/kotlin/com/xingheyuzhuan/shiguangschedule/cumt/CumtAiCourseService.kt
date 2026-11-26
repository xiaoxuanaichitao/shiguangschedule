package com.xingheyuzhuan.shiguangschedule.cumt

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.koin.core.annotation.Single
import kotlin.time.Clock

data class AssistantTurnResult(
    val replyText: String,
    val toolTrace: List<String> = emptyList(),
    val pendingActions: List<CumtAiPendingAction> = emptyList()
)

private data class ParsedToolCall(
    val callId: String,
    val name: String,
    val arguments: JsonObject
)

@Single
class CumtAiCourseService {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private fun createClient(timeoutMs: Long = 90_000L): HttpClient {
        return HttpClient {
            install(HttpTimeout) {
                requestTimeoutMillis = timeoutMs
                connectTimeoutMillis = 15_000L
                socketTimeoutMillis = timeoutMs
            }
        }
    }

    // ==================== 自动发现可用模型 ====================

    suspend fun fetchAvailableModels(
        protocol: CumtAiProtocol,
        baseUrl: String,
        apiKey: String
    ): Result<List<CumtAiModelInfo>> = withContext(Dispatchers.IO) {
        val trimmedKey = apiKey.trim()
        val trimmedBase = baseUrl.trim()
        if (trimmedKey.isBlank()) return@withContext Result.failure(IllegalArgumentException("请先填写 API Key"))
        if (trimmedBase.isBlank()) return@withContext Result.failure(IllegalArgumentException("请先填写 Base URL"))

        val client = createClient(timeoutMs = 25_000L)
        try {
            val models = when (protocol) {
                CumtAiProtocol.OPENAI_COMPATIBLE -> {
                    val endpoint = buildOpenAiModelsEndpoint(trimmedBase)
                    val resp = client.get(endpoint) {
                        header(HttpHeaders.Authorization, "Bearer $trimmedKey")
                    }
                    val body = resp.bodyAsText()
                    if (resp.status.value !in 200..299) {
                        throw IllegalStateException(extractApiErrorMessage(resp.status.value, body))
                    }
                    parseOpenAiModelsJson(body)
                }
                CumtAiProtocol.GEMINI -> {
                    val endpoint = buildGeminiModelsEndpoint(trimmedBase)
                    val resp = client.get(endpoint) {
                        header("x-goog-api-key", trimmedKey)
                    }
                    val body = resp.bodyAsText()
                    if (resp.status.value !in 200..299) {
                        throw IllegalStateException(extractApiErrorMessage(resp.status.value, body))
                    }
                    parseGeminiModelsJson(body)
                }
            }
            if (models.isEmpty()) {
                Result.failure(IllegalStateException("接口未返回可用的对话模型列表，你也可以手动输入模型名称"))
            } else {
                Result.success(models)
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            client.close()
        }
    }

    // ==================== 多模态对话 + Tool Calling 助手 ====================

    suspend fun runAssistantTurn(
        config: CumtAiConfig,
        apiKey: String,
        history: List<CumtAiChatMessage>,
        userText: String,
        imageBytes: ByteArray? = null,
        mimeType: String = "image/jpeg",
        contextHint: String = "",
        toolEngine: ScheduleToolEngine
    ): Result<AssistantTurnResult> = withContext(Dispatchers.IO) {
        val trimmedKey = apiKey.trim()
        val trimmedBase = config.baseUrl.trim()
        val trimmedModel = config.model.trim()

        if (trimmedKey.isBlank()) return@withContext Result.failure(IllegalArgumentException("请先配置并保存 API Key"))
        if (trimmedBase.isBlank()) return@withContext Result.failure(IllegalArgumentException("请先填写 API Base URL"))
        if (trimmedModel.isBlank()) return@withContext Result.failure(IllegalArgumentException("请先选择或填写模型名称"))

        val base64Image = imageBytes?.takeIf { it.isNotEmpty() }?.let { CumtAesCrypto.base64Encode(it) }
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val systemPrompt = buildAssistantSystemPrompt(today.toString(), contextHint)
        val client = createClient(timeoutMs = 95_000L)

        try {
            val result = when (config.protocolEnum) {
                CumtAiProtocol.OPENAI_COMPATIBLE -> runOpenAiAgentLoop(
                    client = client,
                    config = config,
                    apiKey = trimmedKey,
                    systemPrompt = systemPrompt,
                    history = history,
                    userText = userText,
                    mimeType = mimeType,
                    base64Image = base64Image,
                    toolEngine = toolEngine
                )
                CumtAiProtocol.GEMINI -> runGeminiAgentLoop(
                    client = client,
                    config = config,
                    apiKey = trimmedKey,
                    systemPrompt = systemPrompt,
                    history = history,
                    userText = userText,
                    mimeType = mimeType,
                    base64Image = base64Image,
                    toolEngine = toolEngine
                )
            }
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            client.close()
        }
    }

    private suspend fun runOpenAiAgentLoop(
        client: HttpClient,
        config: CumtAiConfig,
        apiKey: String,
        systemPrompt: String,
        history: List<CumtAiChatMessage>,
        userText: String,
        mimeType: String,
        base64Image: String?,
        toolEngine: ScheduleToolEngine
    ): AssistantTurnResult {
        val endpoint = buildOpenAiChatEndpoint(config.baseUrl)
        val messages = mutableListOf<JsonObject>()
        messages.add(buildJsonObject {
            put("role", "system")
            put("content", systemPrompt)
        })

        // 保留最近 10 条历史文本对话作为上下文
        history.takeLast(10).filter { !it.isError && it.text.isNotBlank() }.forEach { msg ->
            messages.add(buildJsonObject {
                put("role", if (msg.role == "user") "user" else "assistant")
                put("content", msg.text.take(2500))
            })
        }

        // 当前用户输入（支持纯文字或图文混合）
        val effectiveUserText = userText.trim().ifBlank {
            if (base64Image != null) "请识别这张图片中的实验安排或课程表，并帮我生成课表导入方案。" else ""
        }
        if (base64Image != null) {
            messages.add(buildJsonObject {
                put("role", "user")
                putJsonArray("content") {
                    addJsonObject {
                        put("type", "text")
                        put("text", effectiveUserText)
                    }
                    addJsonObject {
                        put("type", "image_url")
                        putJsonObject("image_url") {
                            put("url", "data:$mimeType;base64,$base64Image")
                        }
                    }
                }
            })
        } else {
            messages.add(buildJsonObject {
                put("role", "user")
                put("content", effectiveUserText)
            })
        }

        val toolsJson = toolEngine.toOpenAiToolsJson()
        val toolTrace = mutableListOf<String>()
        val pendingActions = mutableListOf<CumtAiPendingAction>()
        var useTools = true

        repeat(4) {
            val payload = buildJsonObject {
                put("model", config.model.trim())
                put("temperature", 0.2)
                put("messages", JsonArray(messages))
                if (useTools) {
                    put("tools", toolsJson)
                    put("tool_choice", "auto")
                }
            }.toString()

            val resp = client.post(endpoint) {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $apiKey")
                setBody(payload)
            }
            val body = resp.bodyAsText()
            if (resp.status.value !in 200..299) {
                // 若部分小模型不支持 tools 参数，自动降级为纯 JSON 结构化回复再试一次
                val lower = body.lowercase()
                if (useTools && resp.status.value == 400 && (lower.contains("tool") || lower.contains("function"))) {
                    useTools = false
                    messages[0] = buildJsonObject {
                        put("role", "system")
                        put("content", systemPrompt + "\n\n" + NO_TOOL_FALLBACK_INSTRUCTION)
                    }
                    return@repeat
                }
                throw IllegalStateException(extractApiErrorMessage(resp.status.value, body))
            }

            val root = json.parseToJsonElement(body).jsonObject
            val choiceMsg = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject?.get("message")?.jsonObject
                ?: return AssistantTurnResult("模型未返回有效回复。", toolTrace, pendingActions)

            val assistantText = extractTextFromContentElement(choiceMsg["content"])
            val toolCalls = parseOpenAiToolCalls(choiceMsg["tool_calls"] as? JsonArray)

            if (toolCalls.isEmpty()) {
                // 若模型在降级模式或直接文本里输出了课程 JSON，则自动提取为待确认的 add_courses 卡片
                val fallbackActions = extractFallbackAddCourseActions(assistantText, toolEngine, config.autoExecuteWrites)
                val finalPending = pendingActions + fallbackActions
                val finalText = sanitizeFallbackReplyText(assistantText, fallbackActions.isNotEmpty())
                return AssistantTurnResult(
                    replyText = finalText.ifBlank {
                        if (finalPending.isNotEmpty()) "已为你解析出以下课表变更，请核对后确认：" else "已完成处理。"
                    },
                    toolTrace = toolTrace,
                    pendingActions = finalPending
                )
            }

            // 把 assistant 的 tool_calls 消息记入上下文
            messages.add(choiceMsg)

            var hasOnlyPendingWrites = true
            for (call in toolCalls) {
                if (!toolEngine.isWriteTool(call.name)) {
                    hasOnlyPendingWrites = false
                    val exec = toolEngine.executeTool(call.name, call.arguments, allowWrite = false)
                    toolTrace.add("🔍 ${call.name}：${exec.summary}")
                    messages.add(buildJsonObject {
                        put("role", "tool")
                        put("tool_call_id", call.callId)
                        put("content", exec.payloadJson.toString())
                    })
                } else if (config.autoExecuteWrites) {
                    hasOnlyPendingWrites = false
                    val preview = toolEngine.buildPendingActionPreview(call.name, call.arguments)
                    val exec = toolEngine.executeTool(call.name, call.arguments, allowWrite = true)
                    toolTrace.add("⚡ ${call.name}：${exec.summary}")
                    pendingActions.add(
                        preview.copy(
                            status = if (exec.success) CumtAiActionStatus.APPLIED else CumtAiActionStatus.FAILED,
                            resultMessage = exec.summary
                        )
                    )
                    messages.add(buildJsonObject {
                        put("role", "tool")
                        put("tool_call_id", call.callId)
                        put("content", exec.payloadJson.toString())
                    })
                } else {
                    val preview = toolEngine.buildPendingActionPreview(call.name, call.arguments)
                    toolTrace.add("📝 待确认 ${call.name}：${preview.title}")
                    pendingActions.add(preview)
                    messages.add(buildJsonObject {
                        put("role", "tool")
                        put("tool_call_id", call.callId)
                        put("content", buildJsonObject {
                            put("status", "pending_user_confirmation")
                            put("title", preview.title)
                            put("summary", preview.summary)
                        }.toString())
                    })
                }
            }

            // 若本轮全是待用户点击确认的写操作，无需多花一次慢请求，直接展示确认卡片给用户
            if (hasOnlyPendingWrites && pendingActions.isNotEmpty()) {
                val reply = assistantText.ifBlank {
                    "我已为你准备好以下课表操作，请核对下方变更卡片并点击「确认应用」："
                }
                return AssistantTurnResult(reply, toolTrace, pendingActions)
            }
        }

        return AssistantTurnResult(
            replyText = if (pendingActions.isNotEmpty()) {
                "已为你生成课表变更方案，请在下方确认应用。"
            } else {
                "已完成信息检索，如有需要请继续补充说明。"
            },
            toolTrace = toolTrace,
            pendingActions = pendingActions
        )
    }

    private suspend fun runGeminiAgentLoop(
        client: HttpClient,
        config: CumtAiConfig,
        apiKey: String,
        systemPrompt: String,
        history: List<CumtAiChatMessage>,
        userText: String,
        mimeType: String,
        base64Image: String?,
        toolEngine: ScheduleToolEngine
    ): AssistantTurnResult {
        val endpoint = buildGeminiEndpoint(config.baseUrl, config.model)
        val contents = mutableListOf<JsonObject>()

        history.takeLast(10).filter { !it.isError && it.text.isNotBlank() }.forEach { msg ->
            contents.add(buildJsonObject {
                put("role", if (msg.role == "user") "user" else "model")
                putJsonArray("parts") {
                    addJsonObject { put("text", msg.text.take(2500)) }
                }
            })
        }

        val effectiveUserText = userText.trim().ifBlank {
            if (base64Image != null) "请识别这张图片中的实验安排或课程表，并帮我生成课表导入方案。" else ""
        }
        contents.add(buildJsonObject {
            put("role", "user")
            putJsonArray("parts") {
                addJsonObject { put("text", effectiveUserText) }
                if (base64Image != null) {
                    addJsonObject {
                        putJsonObject("inlineData") {
                            put("mimeType", mimeType)
                            put("data", base64Image)
                        }
                    }
                }
            }
        })

        val geminiTools = toolEngine.toGeminiToolsJson()
        val toolTrace = mutableListOf<String>()
        val pendingActions = mutableListOf<CumtAiPendingAction>()
        var useTools = true

        repeat(4) {
            val payload = buildJsonObject {
                putJsonObject("systemInstruction") {
                    putJsonArray("parts") {
                        addJsonObject {
                            put("text", if (useTools) systemPrompt else "$systemPrompt\n\n$NO_TOOL_FALLBACK_INSTRUCTION")
                        }
                    }
                }
                put("contents", JsonArray(contents))
                if (useTools) {
                    put("tools", geminiTools)
                }
                putJsonObject("generationConfig") {
                    put("temperature", 0.2)
                }
            }.toString()

            val resp = client.post(endpoint) {
                contentType(ContentType.Application.Json)
                header("x-goog-api-key", apiKey)
                setBody(payload)
            }
            val body = resp.bodyAsText()
            if (resp.status.value !in 200..299) {
                val lower = body.lowercase()
                if (useTools && resp.status.value == 400 && (lower.contains("function") || lower.contains("tool"))) {
                    useTools = false
                    return@repeat
                }
                throw IllegalStateException(extractApiErrorMessage(resp.status.value, body))
            }

            val root = json.parseToJsonElement(body).jsonObject
            val modelContent = root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject?.get("content")?.jsonObject
                ?: return AssistantTurnResult("Gemini 未返回有效内容。", toolTrace, pendingActions)
            val parts = modelContent["parts"]?.jsonArray ?: JsonArray(emptyList())

            val textParts = mutableListOf<String>()
            val toolCalls = mutableListOf<ParsedToolCall>()
            parts.forEachIndexed { idx, partElem ->
                val obj = partElem as? JsonObject ?: return@forEachIndexed
                obj["text"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }?.let { textParts.add(it) }
                val fc = obj["functionCall"] as? JsonObject
                if (fc != null) {
                    val name = fc["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val args = (fc["args"] as? JsonObject) ?: JsonObject(emptyMap())
                    if (name.isNotBlank()) {
                        toolCalls.add(ParsedToolCall("gemini_call_$idx", name, args))
                    }
                }
            }

            val assistantText = textParts.joinToString("\n").trim()
            if (toolCalls.isEmpty()) {
                val fallbackActions = extractFallbackAddCourseActions(assistantText, toolEngine, config.autoExecuteWrites)
                val finalPending = pendingActions + fallbackActions
                val finalText = sanitizeFallbackReplyText(assistantText, fallbackActions.isNotEmpty())
                return AssistantTurnResult(
                    replyText = finalText.ifBlank {
                        if (finalPending.isNotEmpty()) "已为你解析出以下课表变更，请核对后确认：" else "已完成处理。"
                    },
                    toolTrace = toolTrace,
                    pendingActions = finalPending
                )
            }

            contents.add(modelContent)

            val responseParts = mutableListOf<JsonObject>()
            var hasOnlyPendingWrites = true
            for (call in toolCalls) {
                val resultObj = if (!toolEngine.isWriteTool(call.name)) {
                    hasOnlyPendingWrites = false
                    val exec = toolEngine.executeTool(call.name, call.arguments, allowWrite = false)
                    toolTrace.add("🔍 ${call.name}：${exec.summary}")
                    exec.payloadJson
                } else if (config.autoExecuteWrites) {
                    hasOnlyPendingWrites = false
                    val preview = toolEngine.buildPendingActionPreview(call.name, call.arguments)
                    val exec = toolEngine.executeTool(call.name, call.arguments, allowWrite = true)
                    toolTrace.add("⚡ ${call.name}：${exec.summary}")
                    pendingActions.add(
                        preview.copy(
                            status = if (exec.success) CumtAiActionStatus.APPLIED else CumtAiActionStatus.FAILED,
                            resultMessage = exec.summary
                        )
                    )
                    exec.payloadJson
                } else {
                    val preview = toolEngine.buildPendingActionPreview(call.name, call.arguments)
                    toolTrace.add("📝 待确认 ${call.name}：${preview.title}")
                    pendingActions.add(preview)
                    buildJsonObject {
                        put("status", "pending_user_confirmation")
                        put("title", preview.title)
                        put("summary", preview.summary)
                    }
                }
                responseParts.add(buildJsonObject {
                    putJsonObject("functionResponse") {
                        put("name", call.name)
                        put("response", resultObj)
                    }
                })
            }

            if (hasOnlyPendingWrites && pendingActions.isNotEmpty()) {
                val reply = assistantText.ifBlank {
                    "我已为你准备好以下课表操作，请核对下方变更卡片并点击「确认应用」："
                }
                return AssistantTurnResult(reply, toolTrace, pendingActions)
            }

            contents.add(buildJsonObject {
                put("role", "user")
                put("parts", JsonArray(responseParts))
            })
        }

        return AssistantTurnResult(
            replyText = if (pendingActions.isNotEmpty()) "已为你生成课表变更方案，请在下方确认应用。" else "已完成查询。",
            toolTrace = toolTrace,
            pendingActions = pendingActions
        )
    }

    private suspend fun extractFallbackAddCourseActions(
        assistantText: String,
        toolEngine: ScheduleToolEngine,
        autoExecute: Boolean
    ): List<CumtAiPendingAction> {
        val parsed = CumtParsers.parseAiRecognizedCoursesJson(assistantText)
        if (parsed.isEmpty()) return emptyList()
        val argsObj = buildJsonObject {
            putJsonArray("courses") {
                parsed.forEach { c ->
                    addJsonObject {
                        put("name", c.name)
                        put("teacher", c.teacher)
                        put("position", c.position)
                        put("day", c.day)
                        put("startSection", c.startSection)
                        put("endSection", c.endSection)
                        putJsonArray("weeks") { c.weeks.forEach { add(it) } }
                        if (c.remark.isNotBlank()) put("remark", c.remark)
                    }
                }
            }
        }
        val preview = toolEngine.buildPendingActionPreview("add_courses", argsObj)
        return if (autoExecute) {
            val exec = toolEngine.executeTool("add_courses", argsObj, allowWrite = true)
            listOf(
                preview.copy(
                    status = if (exec.success) CumtAiActionStatus.APPLIED else CumtAiActionStatus.FAILED,
                    resultMessage = exec.summary
                )
            )
        } else {
            listOf(preview)
        }
    }

    private fun sanitizeFallbackReplyText(text: String, extractedJsonAction: Boolean): String {
        if (!extractedJsonAction) return text.trim()
        val stripped = text.replace(Regex("""```(?:json)?[\s\S]*?```""", RegexOption.IGNORE_CASE), "").trim()
        return if (stripped.isBlank() || stripped.startsWith("{") || stripped.startsWith("[")) {
            "我已根据你的描述/图片整理好以下课程安排，请核对后点击「确认应用」："
        } else {
            stripped
        }
    }

    private fun parseOpenAiToolCalls(array: JsonArray?): List<ParsedToolCall> {
        if (array == null) return emptyList()
        return array.mapIndexedNotNull { idx, elem ->
            val obj = elem as? JsonObject ?: return@mapIndexedNotNull null
            val callId = obj["id"]?.jsonPrimitive?.contentOrNull?.ifBlank { "call_$idx" } ?: "call_$idx"
            val fn = obj["function"] as? JsonObject ?: return@mapIndexedNotNull null
            val name = fn["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
            if (name.isBlank()) return@mapIndexedNotNull null
            val rawArgs = fn["arguments"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val argsObj = runCatching {
                if (rawArgs.isBlank()) JsonObject(emptyMap())
                else json.parseToJsonElement(rawArgs).jsonObject
            }.getOrDefault(JsonObject(emptyMap()))
            ParsedToolCall(callId, name, argsObj)
        }
    }

    private fun extractTextFromContentElement(content: JsonElement?): String {
        if (content == null) return ""
        return when (content) {
            is JsonArray -> content.mapNotNull { part ->
                (part as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull
            }.joinToString("\n").trim()
            else -> content.jsonPrimitive.contentOrNull.orEmpty().trim()
        }
    }

    // ==================== 兼容旧单次识图 & 连通测试 ====================

    suspend fun recognizeCoursesFromImage(
        config: CumtAiConfig,
        apiKey: String,
        imageBytes: ByteArray,
        mimeType: String = "image/jpeg",
        userHint: String = ""
    ): Result<List<CumtAiRecognizedCourse>> = withContext(Dispatchers.IO) {
        val trimmedKey = apiKey.trim()
        val trimmedBase = config.baseUrl.trim()
        val trimmedModel = config.model.trim()

        if (trimmedKey.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("请先配置并保存 API Key"))
        }
        if (trimmedBase.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("请先填写 API Base URL"))
        }
        if (trimmedModel.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("请先填写支持视觉识别的模型名称"))
        }
        if (imageBytes.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("图片内容为空，请重新选择"))
        }

        val base64Image = CumtAesCrypto.base64Encode(imageBytes)
        val client = createClient(timeoutMs = 90_000L)
        try {
            val rawText = when (config.protocolEnum) {
                CumtAiProtocol.OPENAI_COMPATIBLE -> callOpenAiCompatibleVision(
                    client = client,
                    baseUrl = trimmedBase,
                    model = trimmedModel,
                    apiKey = trimmedKey,
                    mimeType = mimeType,
                    base64Image = base64Image,
                    userHint = userHint
                )
                CumtAiProtocol.GEMINI -> callGeminiVision(
                    client = client,
                    baseUrl = trimmedBase,
                    model = trimmedModel,
                    apiKey = trimmedKey,
                    mimeType = mimeType,
                    base64Image = base64Image,
                    userHint = userHint
                )
            }
            val parsed = CumtParsers.parseAiRecognizedCoursesJson(rawText)
            if (parsed.isEmpty()) {
                Result.failure(
                    IllegalStateException("AI 已返回结果，但未解析出包含周次/星期/节次的有效课程，请确认图片清晰或补充提示词后重试")
                )
            } else {
                Result.success(parsed)
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            client.close()
        }
    }

    suspend fun testConnection(
        config: CumtAiConfig,
        apiKey: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val trimmedKey = apiKey.trim()
        val trimmedBase = config.baseUrl.trim()
        val trimmedModel = config.model.trim()
        if (trimmedKey.isBlank()) return@withContext Result.failure(IllegalArgumentException("请输入 API Key"))
        if (trimmedBase.isBlank()) return@withContext Result.failure(IllegalArgumentException("请输入 Base URL"))
        if (trimmedModel.isBlank()) return@withContext Result.failure(IllegalArgumentException("请输入模型名称"))

        val client = createClient(timeoutMs = 25_000L)
        try {
            val reply = when (config.protocolEnum) {
                CumtAiProtocol.OPENAI_COMPATIBLE -> {
                    val endpoint = buildOpenAiChatEndpoint(trimmedBase)
                    val payload = buildJsonObject {
                        put("model", trimmedModel)
                        put("max_tokens", 16)
                        put("temperature", 0)
                        putJsonArray("messages") {
                            addJsonObject {
                                put("role", "user")
                                put("content", "Reply OK")
                            }
                        }
                    }.toString()
                    val resp = client.post(endpoint) {
                        contentType(ContentType.Application.Json)
                        header(HttpHeaders.Authorization, "Bearer $trimmedKey")
                        setBody(payload)
                    }
                    val body = resp.bodyAsText()
                    if (resp.status.value !in 200..299) {
                        throw IllegalStateException(extractApiErrorMessage(resp.status.value, body))
                    }
                    extractOpenAiContent(body).ifBlank { "连接正常" }
                }
                CumtAiProtocol.GEMINI -> {
                    val endpoint = buildGeminiEndpoint(trimmedBase, trimmedModel)
                    val payload = buildJsonObject {
                        putJsonArray("contents") {
                            addJsonObject {
                                put("role", "user")
                                putJsonArray("parts") {
                                    addJsonObject { put("text", "Reply OK") }
                                }
                            }
                        }
                        putJsonObject("generationConfig") {
                            put("maxOutputTokens", 16)
                            put("temperature", 0)
                        }
                    }.toString()
                    val resp = client.post(endpoint) {
                        contentType(ContentType.Application.Json)
                        header("x-goog-api-key", trimmedKey)
                        setBody(payload)
                    }
                    val body = resp.bodyAsText()
                    if (resp.status.value !in 200..299) {
                        throw IllegalStateException(extractApiErrorMessage(resp.status.value, body))
                    }
                    extractGeminiContent(body).ifBlank { "连接正常" }
                }
            }
            Result.success("连通成功（$trimmedModel）：${reply.take(40)}")
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            client.close()
        }
    }

    private suspend fun callOpenAiCompatibleVision(
        client: HttpClient,
        baseUrl: String,
        model: String,
        apiKey: String,
        mimeType: String,
        base64Image: String,
        userHint: String
    ): String {
        val endpoint = buildOpenAiChatEndpoint(baseUrl)
        val payload = buildOpenAiVisionPayload(
            model = model,
            mimeType = mimeType,
            base64Image = base64Image,
            userHint = userHint
        )
        val resp = client.post(endpoint) {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $apiKey")
            setBody(payload)
        }
        val body = resp.bodyAsText()
        if (resp.status.value !in 200..299) {
            throw IllegalStateException(extractApiErrorMessage(resp.status.value, body))
        }
        return extractOpenAiContent(body)
    }

    private suspend fun callGeminiVision(
        client: HttpClient,
        baseUrl: String,
        model: String,
        apiKey: String,
        mimeType: String,
        base64Image: String,
        userHint: String
    ): String {
        val endpoint = buildGeminiEndpoint(baseUrl, model)
        val payload = buildGeminiVisionPayload(
            mimeType = mimeType,
            base64Image = base64Image,
            userHint = userHint
        )
        val resp = client.post(endpoint) {
            contentType(ContentType.Application.Json)
            header("x-goog-api-key", apiKey)
            setBody(payload)
        }
        val body = resp.bodyAsText()
        if (resp.status.value !in 200..299) {
            throw IllegalStateException(extractApiErrorMessage(resp.status.value, body))
        }
        return extractGeminiContent(body)
    }

    private fun extractOpenAiContent(body: String): String {
        val root = json.parseToJsonElement(body).jsonObject
        val choices = root["choices"]?.jsonArray
        val firstChoice = choices?.firstOrNull()?.jsonObject ?: return ""
        val message = firstChoice["message"]?.jsonObject ?: return ""
        return extractTextFromContentElement(message["content"])
    }

    private fun extractGeminiContent(body: String): String {
        val root = json.parseToJsonElement(body).jsonObject
        val candidates = root["candidates"]?.jsonArray
        val first = candidates?.firstOrNull()?.jsonObject ?: return ""
        val parts = first["content"]?.jsonObject?.get("parts")?.jsonArray ?: return ""
        return parts.mapNotNull { part ->
            (part as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull
        }.joinToString("\n")
    }

    private fun extractApiErrorMessage(statusCode: Int, body: String): String {
        val parsedMsg = runCatching {
            val root = json.parseToJsonElement(body).jsonObject
            val errObj = root["error"] as? JsonObject
            errObj?.get("message")?.jsonPrimitive?.contentOrNull
                ?: root["message"]?.jsonPrimitive?.contentOrNull
                ?: root["msg"]?.jsonPrimitive?.contentOrNull
        }.getOrNull()?.takeIf { it.isNotBlank() }

        val prefix = when (statusCode) {
            401 -> "API Key 无效或未授权 (401)"
            402 -> "账号余额不足 (402)"
            403 -> "无权限访问该模型或接口 (403)"
            404 -> "接口地址或模型名称不存在 (404)"
            429 -> "请求频率超限或额度已满 (429)"
            else -> "AI 接口请求失败 (HTTP $statusCode)"
        }
        return if (parsedMsg != null) "$prefix：${parsedMsg.take(180)}" else prefix
    }

    companion object {
        private val staticJson = Json { ignoreUnknownKeys = true; isLenient = true }

        private const val NO_TOOL_FALLBACK_INSTRUCTION = """注意：当前模型不支持原生函数调用。如果用户想新增课程或识别实验表图片，请在回答末尾附上一个 ```json 代码块，格式为 {"courses":[{"name":"...","teacher":"...","position":"...","day":6,"startSection":1,"endSection":2,"weeks":[11],"remark":"..."}]}，应用会自动解析为确认卡片。"""

        fun buildAssistantSystemPrompt(todayStr: String, contextHint: String): String = buildString {
            append(
                """你是「矿大时光课表」内置的智能校园与课表 AI 助手，擅长处理：
1. 自然语言文字叙述加课、批量导入、调课、停课、修改教室/时间、删除课程；
2. 识别实验安排表、课程表或群通知截图（若图中有红框/画圈/勾选标注，默认仅提取被标记条目；自动跳过尚未排具体周次/星期/节次的空行）；
3. 查询某周课表、查找空闲节次、分析矿大考试倒计时与成绩绩点。

当前日期：$todayStr。"""
            )
            if (contextHint.isNotBlank()) {
                append("\n当前用户所在界面上下文：$contextHint。")
            }
            append(
                """

操作规范：
- 回答涉及“本周/下周/今天/明天”或现有课程时，优先调用 `list_course_tables`、`get_courses`、`find_free_slots` 或 `get_exams_and_scores` 获取真实数据，严禁凭空编造课程。
- 用户要求新增课程或上传实验表图片时，调用 `add_courses` 工具（星期用 1~7 整数，周日=7；weeks 用整数数组如 [11] 或 [11,12]；矿大实验表项目名称如“二阶系统的动态性能分析(2学时)”可保留简洁名称，把次数/批次/学时放进 remark）。
- 用户要求修改、调课、单周停课或删课时，先调用 `get_courses` 找到准确 `courseId`，再调用 `update_course` / `reschedule_course` / `delete_courses`。
- 写操作会以可视化卡片形式呈现给用户确认，因此你直接发起准确的工具调用并用简明中文回复即可。"""
            )
        }

        internal const val SYSTEM_PROMPT = """你是一个专业的大学实验安排表与课程表视觉解析助手（尤其熟悉中国矿业大学实验排课表格）。
请从用户提供的图片中准确提取每一条已排定具体时间的实验课或普通课程，并严格返回 JSON。

核心解析规则：
1. 标注框优先：如果图片中有红框、彩框、画圈、勾选或箭头等人工标记，请优先且仅提取被框选/标记的条目（除非用户补充说明另有要求）。
2. 跳过未排时间条目：如果某一行只有教师姓名或人数，没有具体的周次、星期或节次，必须跳过，不要编造时间。
3. 常见矿大实验表字段对应：
   - 项目名称（如“二阶系统的动态性能分析(2学时)”）→ name 填简洁实验名（如“二阶系统的动态性能分析”），可将学时/次数/批次（如“第1次 · 第1批 · 2学时”）写入 remark。
   - 教师（如“周晨”）→ teacher。
   - 地点/实验室（如“信控-A205”）→ position。
   - 周次（如“11周”、“12周”、“1-4周”）→ weeks 填整数数组，如 [11]、[12]、[1,2,3,4]。
   - 星期（如“星期6”、“星期7”、“周日”）→ day 填 1~7 的整数（周一=1 ... 周六=6，星期7/周日=7）。
   - 节次（如“节 1-2”、“3-4”、“1-4”、“5-8”）→ startSection 与 endSection 填起止节次整数。
4. 仅返回合法的 JSON 对象，不要包含任何解释文字或 Markdown 标记，格式如下：
{
  "courses": [
    {
      "name": "二阶系统的动态性能分析",
      "teacher": "周晨",
      "position": "信控-A205",
      "day": 6,
      "startSection": 1,
      "endSection": 2,
      "weeks": [11],
      "remark": "第1次 · 第1批 · 2学时"
    }
  ]
}"""

        fun buildUserPrompt(userHint: String): String = buildString {
            append("请识别这张图片中的实验安排或课程表，按规定 JSON 格式输出所有已排时间的条目。")
            val trimmedHint = userHint.trim()
            if (trimmedHint.isNotEmpty()) {
                append("\n用户额外要求：")
                append(trimmedHint)
            }
        }

        fun buildOpenAiChatEndpoint(baseUrl: String): String {
            val cleaned = baseUrl.trim().trimEnd('/')
            return if (cleaned.endsWith("/chat/completions", ignoreCase = true)) {
                cleaned
            } else {
                "$cleaned/chat/completions"
            }
        }

        fun buildOpenAiModelsEndpoint(baseUrl: String): String {
            val cleaned = baseUrl.trim().trimEnd('/')
            return when {
                cleaned.endsWith("/chat/completions", ignoreCase = true) ->
                    cleaned.removeSuffix("/chat/completions").removeSuffix("/CHAT/COMPLETIONS") + "/models"
                cleaned.endsWith("/models", ignoreCase = true) -> cleaned
                else -> "$cleaned/models"
            }
        }

        fun buildGeminiEndpoint(baseUrl: String, model: String): String {
            val cleanedBase = baseUrl.trim().trimEnd('/')
            val cleanedModel = model.trim().removePrefix("models/")
            return if (cleanedBase.contains(":generateContent", ignoreCase = true)) {
                cleanedBase
            } else {
                "$cleanedBase/models/$cleanedModel:generateContent"
            }
        }

        fun buildGeminiModelsEndpoint(baseUrl: String): String {
            val cleaned = baseUrl.trim().trimEnd('/')
            val root = cleaned.substringBefore("/models").trimEnd('/')
            return "$root/models?pageSize=100"
        }

        fun parseOpenAiModelsJson(rawBody: String): List<CumtAiModelInfo> {
            val root = runCatching { staticJson.parseToJsonElement(rawBody).jsonObject }.getOrNull() ?: return emptyList()
            val arr = (root["data"] as? JsonArray) ?: (root["models"] as? JsonArray) ?: return emptyList()
            val blockedTokens = listOf(
                "embedding", "rerank", "whisper", "tts", "dall-e", "moderation",
                "bge-", "speech", "audio-preview", "realtime", "transcribe", "flux", "stable-diffusion", "kolors", "wan2", "cogvideo"
            )
            return arr.mapNotNull { elem ->
                val obj = elem as? JsonObject ?: return@mapNotNull null
                val id = obj["id"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                if (id.isBlank()) return@mapNotNull null
                val lower = id.lowercase()
                if (blockedTokens.any { lower.contains(it) }) return@mapNotNull null
                inferModelInfo(id = id, displayName = id)
            }.distinctBy { it.id }
                .sortedWith(
                    compareByDescending<CumtAiModelInfo> { it.supportsVision }
                        .thenByDescending { it.supportsTools }
                        .thenBy { it.id.lowercase() }
                )
        }

        fun parseGeminiModelsJson(rawBody: String): List<CumtAiModelInfo> {
            val root = runCatching { staticJson.parseToJsonElement(rawBody).jsonObject }.getOrNull() ?: return emptyList()
            val arr = root["models"] as? JsonArray ?: return emptyList()
            return arr.mapNotNull { elem ->
                val obj = elem as? JsonObject ?: return@mapNotNull null
                val rawName = obj["name"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                if (rawName.isBlank()) return@mapNotNull null
                val methods = (obj["supportedGenerationMethods"] as? JsonArray)
                    ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                    .orEmpty()
                if (methods.isNotEmpty() && methods.none { it.equals("generateContent", ignoreCase = true) }) {
                    return@mapNotNull null
                }
                val id = rawName.removePrefix("models/")
                val lower = id.lowercase()
                if (lower.contains("embedding") || lower.contains("aqa") || lower.contains("imagen")) {
                    return@mapNotNull null
                }
                val displayName = obj["displayName"]?.jsonPrimitive?.contentOrNull?.trim()?.ifBlank { id } ?: id
                inferModelInfo(
                    id = id,
                    displayName = displayName,
                    forceVision = lower.contains("gemini")
                )
            }.distinctBy { it.id }
                .sortedWith(
                    compareByDescending<CumtAiModelInfo> { it.id.contains("2.5") || it.id.contains("3.") }
                        .thenByDescending { it.id.contains("flash") }
                        .thenBy { it.id.lowercase() }
                )
        }

        fun inferModelInfo(
            id: String,
            displayName: String = id,
            forceVision: Boolean = false
        ): CumtAiModelInfo {
            val lower = id.lowercase()
            val isVision = forceVision ||
                lower.contains("-vl") ||
                lower.contains("vl-") ||
                lower.contains("vision") ||
                lower.contains("4o") ||
                lower.contains("4.1") ||
                lower.contains("gpt-5") ||
                lower.contains("gemini") ||
                lower.contains("claude-3") ||
                lower.contains("claude-4") ||
                lower.contains("glm-4v") ||
                lower.contains("glm-4.5v") ||
                lower.contains("glm-4.6v") ||
                lower.contains("pixtral") ||
                lower.contains("qvq") ||
                lower.contains("internvl") ||
                lower.contains("minicpm-v") ||
                lower.contains("doubao-1-5-vision") ||
                lower.contains("doubao-seed") ||
                lower.contains("kimi-vl")
            val isReasoning = lower.contains("r1") ||
                lower.contains("o1") ||
                lower.contains("o3") ||
                lower.contains("o4") ||
                lower.contains("thinking") ||
                lower.contains("qwq") ||
                lower.contains("qvq") ||
                lower.contains("reasoner")
            val supportsTools = !lower.contains("o1-mini") && !lower.contains("qvq")
            return CumtAiModelInfo(
                id = id,
                displayName = displayName,
                supportsVision = isVision,
                supportsTools = supportsTools,
                isReasoning = isReasoning
            )
        }

        fun buildOpenAiVisionPayload(
            model: String,
            mimeType: String,
            base64Image: String,
            userHint: String = ""
        ): String {
            return buildJsonObject {
                put("model", model.trim())
                put("temperature", 0.1)
                putJsonArray("messages") {
                    addJsonObject {
                        put("role", "system")
                        put("content", SYSTEM_PROMPT)
                    }
                    addJsonObject {
                        put("role", "user")
                        putJsonArray("content") {
                            addJsonObject {
                                put("type", "text")
                                put("text", buildUserPrompt(userHint))
                            }
                            addJsonObject {
                                put("type", "image_url")
                                putJsonObject("image_url") {
                                    put("url", "data:$mimeType;base64,$base64Image")
                                }
                            }
                        }
                    }
                }
            }.toString()
        }

        fun buildGeminiVisionPayload(
            mimeType: String,
            base64Image: String,
            userHint: String = ""
        ): String {
            return buildJsonObject {
                putJsonObject("systemInstruction") {
                    putJsonArray("parts") {
                        addJsonObject { put("text", SYSTEM_PROMPT) }
                    }
                }
                putJsonArray("contents") {
                    addJsonObject {
                        put("role", "user")
                        putJsonArray("parts") {
                            addJsonObject { put("text", buildUserPrompt(userHint)) }
                            addJsonObject {
                                putJsonObject("inlineData") {
                                    put("mimeType", mimeType)
                                    put("data", base64Image)
                                }
                            }
                        }
                    }
                }
                putJsonObject("generationConfig") {
                    put("temperature", 0.1)
                    put("responseMimeType", "application/json")
                }
            }.toString()
        }
    }
}
