package com.xingheyuzhuan.shiguangschedule.cumt

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.koin.core.annotation.Single

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
        val content = message["content"] ?: return ""
        return when (content) {
            is JsonArray -> content.mapNotNull { part ->
                (part as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull
            }.joinToString("\n")
            else -> content.jsonPrimitive.contentOrNull.orEmpty()
        }
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

        fun buildGeminiEndpoint(baseUrl: String, model: String): String {
            val cleanedBase = baseUrl.trim().trimEnd('/')
            val cleanedModel = model.trim().removePrefix("models/")
            return if (cleanedBase.contains(":generateContent", ignoreCase = true)) {
                cleanedBase
            } else {
                "$cleanedBase/models/$cleanedModel:generateContent"
            }
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
