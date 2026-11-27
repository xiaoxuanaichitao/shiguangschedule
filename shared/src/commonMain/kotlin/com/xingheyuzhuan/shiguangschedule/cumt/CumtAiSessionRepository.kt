package com.xingheyuzhuan.shiguangschedule.cumt

import androidx.compose.ui.graphics.ImageBitmap
import com.xingheyuzhuan.shiguangschedule.ui.components.ToastManager
import com.xingheyuzhuan.shiguangschedule.ui.components.encodeImageBitmapForAi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.annotation.Single
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * 全局共享的 AI 助手会话中心。
 * 支持跨重启会话持久化、手动停止生成、课表冲突提示与一键撤回已导入课程。
 */
@OptIn(ExperimentalUuidApi::class)
@Single
class CumtAiSessionRepository(
    private val localRepo: CumtLocalRepository,
    private val aiService: CumtAiCourseService,
    private val toolEngine: ScheduleToolEngine
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var activeTurnJob: Job? = null

    private val _messages = MutableStateFlow<List<CumtAiChatMessage>>(emptyList())
    val messages: StateFlow<List<CumtAiChatMessage>> = _messages.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _hasUnreadReply = MutableStateFlow(false)
    val hasUnreadReply: StateFlow<Boolean> = _hasUnreadReply.asStateFlow()

    private val _isQuickSheetOpen = MutableStateFlow(false)
    val isQuickSheetOpen: StateFlow<Boolean> = _isQuickSheetOpen.asStateFlow()

    private val _isFetchingModels = MutableStateFlow(false)
    val isFetchingModels: StateFlow<Boolean> = _isFetchingModels.asStateFlow()

    private val _modelFetchNotice = MutableStateFlow<String?>(null)
    val modelFetchNotice: StateFlow<String?> = _modelFetchNotice.asStateFlow()

    init {
        scope.launch {
            val saved = localRepo.loadSavedChatHistory()
            if (saved.isNotEmpty() && _messages.value.isEmpty()) {
                _messages.value = saved
            }
        }
    }

    private fun persistCurrentMessages() {
        val snapshot = _messages.value
        scope.launch {
            localRepo.saveChatHistory(snapshot)
        }
    }

    fun setQuickSheetOpen(open: Boolean) {
        _isQuickSheetOpen.value = open
        if (open) _hasUnreadReply.value = false
    }

    fun markRepliesRead() {
        _hasUnreadReply.value = false
    }

    fun clearConversation() {
        activeTurnJob?.cancel()
        activeTurnJob = null
        _isBusy.value = false
        _messages.value = emptyList()
        _hasUnreadReply.value = false
        persistCurrentMessages()
    }

    fun cancelCurrentTurn() {
        val job = activeTurnJob
        if (job != null && job.isActive) {
            job.cancel()
            activeTurnJob = null
            _isBusy.value = false
            val stopMsg = CumtAiChatMessage(
                id = "s_${Uuid.random().toString().take(8)}",
                role = "assistant",
                text = "已手动停止本次回复。",
                timestampMillis = Clock.System.now().toEpochMilliseconds()
            )
            _messages.update { it + stopMsg }
            persistCurrentMessages()
        }
    }

    fun refreshAvailableModels(
        protocol: CumtAiProtocol,
        baseUrl: String,
        apiKey: String,
        providerIdOverride: String? = null,
        onModelsLoaded: (List<CumtAiModelInfo>) -> Unit = {}
    ) {
        if (_isFetchingModels.value) return
        scope.launch {
            _isFetchingModels.value = true
            _modelFetchNotice.value = null
            aiService.fetchAvailableModels(protocol, baseUrl, apiKey).fold(
                onSuccess = { models ->
                    localRepo.saveCachedAiModels(models, providerIdOverride)
                    val visionCount = models.count { it.supportsVision }
                    _modelFetchNotice.value = "已自动识别 ${models.size} 个可用模型（含 $visionCount 个视觉模型）"
                    onModelsLoaded(models)
                },
                onFailure = { err ->
                    _modelFetchNotice.value = "拉取模型失败：${err.message ?: "请检查 Key 或 Base URL"}"
                }
            )
            _isFetchingModels.value = false
        }
    }

    fun sendUserMessage(
        text: String,
        image: ImageBitmap? = null,
        contextHint: String = "",
        onNeedApiKey: () -> Unit = {}
    ) {
        val cleanText = text.trim()
        if (cleanText.isEmpty() && image == null) return
        if (_isBusy.value) {
            ToastManager.show("AI 正在回复，可点击右侧停止按钮中断")
            return
        }

        activeTurnJob = scope.launch {
            val config = localRepo.aiConfigFlow.first()
            val apiKey = localRepo.getSavedAiApiKey()
            if (apiKey.isBlank()) {
                ToastManager.show("请先配置并保存 AI API Key")
                onNeedApiKey()
                return@launch
            }

            val historySnapshot = _messages.value
            val userMsg = CumtAiChatMessage(
                id = "u_${Uuid.random().toString().take(8)}",
                role = "user",
                text = cleanText.ifBlank { "请识别这张图片并导入到课表" },
                hasImage = image != null,
                imageNote = if (image != null) "已附带图片（${image.width}×${image.height}）" else "",
                timestampMillis = Clock.System.now().toEpochMilliseconds()
            )
            _messages.update { it + userMsg }
            persistCurrentMessages()
            _isBusy.value = true

            try {
                val jpegBytes = image?.let { encodeImageBitmapForAi(it, maxDimension = 1920, quality = 85) }
                aiService.runAssistantTurn(
                    config = config,
                    apiKey = apiKey,
                    history = historySnapshot,
                    userText = cleanText,
                    imageBytes = jpegBytes,
                    mimeType = "image/jpeg",
                    contextHint = contextHint,
                    toolEngine = toolEngine
                ).fold(
                    onSuccess = { turn ->
                        val assistantMsg = CumtAiChatMessage(
                            id = "a_${Uuid.random().toString().take(8)}",
                            role = "assistant",
                            text = turn.replyText,
                            toolTrace = turn.toolTrace,
                            pendingActions = turn.pendingActions,
                            timestampMillis = Clock.System.now().toEpochMilliseconds()
                        )
                        _messages.update { it + assistantMsg }
                        persistCurrentMessages()
                        if (!_isQuickSheetOpen.value) {
                            _hasUnreadReply.value = true
                        }
                    },
                    onFailure = { err ->
                        if (err is CancellationException) throw err
                        val errMsg = CumtAiChatMessage(
                            id = "e_${Uuid.random().toString().take(8)}",
                            role = "assistant",
                            text = "请求失败：${err.message ?: "请检查网络、API Key 或所选模型是否支持图片/对话"}",
                            isError = true,
                            timestampMillis = Clock.System.now().toEpochMilliseconds()
                        )
                        _messages.update { it + errMsg }
                        persistCurrentMessages()
                    }
                )
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                val errMsg = CumtAiChatMessage(
                    id = "e_${Uuid.random().toString().take(8)}",
                    role = "assistant",
                    text = "处理异常：${e.message ?: "未知错误"}",
                    isError = true,
                    timestampMillis = Clock.System.now().toEpochMilliseconds()
                )
                _messages.update { it + errMsg }
                persistCurrentMessages()
            } finally {
                _isBusy.value = false
                activeTurnJob = null
            }
        }
    }

    fun confirmPendingAction(messageId: String, actionId: String) {
        scope.launch {
            val targetAction = _messages.value
                .firstOrNull { it.id == messageId }
                ?.pendingActions
                ?.firstOrNull { it.id == actionId }
                ?: return@launch
            if (targetAction.status != CumtAiActionStatus.PENDING) return@launch

            val result = toolEngine.executePendingAction(targetAction)
            updateActionState(
                messageId = messageId,
                actionId = actionId,
                status = if (result.success) CumtAiActionStatus.APPLIED else CumtAiActionStatus.FAILED,
                resultMessage = result.summary,
                createdCourseIds = result.createdCourseIds
            )
            ToastManager.show(result.summary)
        }
    }

    fun undoAppliedAction(messageId: String, actionId: String) {
        scope.launch {
            val targetAction = _messages.value
                .firstOrNull { it.id == messageId }
                ?.pendingActions
                ?.firstOrNull { it.id == actionId }
                ?: return@launch
            if (targetAction.status != CumtAiActionStatus.APPLIED || targetAction.createdCourseIds.isEmpty()) {
                return@launch
            }
            val ok = toolEngine.undoCreatedCourses(targetAction.createdCourseIds)
            if (ok) {
                updateActionState(
                    messageId = messageId,
                    actionId = actionId,
                    status = CumtAiActionStatus.UNDONE,
                    resultMessage = "已撤回本次导入的 ${targetAction.createdCourseIds.size} 条课程",
                    createdCourseIds = emptyList()
                )
                ToastManager.show("已撤回导入")
            } else {
                ToastManager.show("撤回失败")
            }
        }
    }

    fun rejectPendingAction(messageId: String, actionId: String) {
        updateActionState(
            messageId = messageId,
            actionId = actionId,
            status = CumtAiActionStatus.REJECTED,
            resultMessage = "已取消该操作"
        )
    }

    fun confirmAllPendingActions(messageId: String) {
        scope.launch {
            val pending = _messages.value
                .firstOrNull { it.id == messageId }
                ?.pendingActions
                ?.filter { it.status == CumtAiActionStatus.PENDING }
                .orEmpty()
            var appliedCount = 0
            for (action in pending) {
                val result = toolEngine.executePendingAction(action)
                if (result.success) appliedCount++
                updateActionState(
                    messageId = messageId,
                    actionId = action.id,
                    status = if (result.success) CumtAiActionStatus.APPLIED else CumtAiActionStatus.FAILED,
                    resultMessage = result.summary,
                    createdCourseIds = result.createdCourseIds
                )
            }
            if (appliedCount > 0) {
                ToastManager.show("已应用 $appliedCount 项课表变更")
            }
        }
    }

    private fun updateActionState(
        messageId: String,
        actionId: String,
        status: CumtAiActionStatus,
        resultMessage: String,
        createdCourseIds: List<String>? = null
    ) {
        _messages.update { list ->
            list.map { msg ->
                if (msg.id != messageId) msg
                else msg.copy(
                    pendingActions = msg.pendingActions.map { act ->
                        if (act.id != actionId) act
                        else act.copy(
                            status = status,
                            resultMessage = resultMessage,
                            createdCourseIds = createdCourseIds ?: act.createdCourseIds
                        )
                    }
                )
            }
        }
        persistCurrentMessages()
    }
}
