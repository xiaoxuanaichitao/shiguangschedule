package com.xingheyuzhuan.shiguangschedule.ui.cumt

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.xingheyuzhuan.shiguangschedule.cumt.CumtAiOrbIconStyle
import com.xingheyuzhuan.shiguangschedule.ui.components.encodeImageBitmapForAi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path
import org.koin.core.annotation.Named
import shiguangschedule.shared.generated.resources.image_24px
import kotlin.math.roundToInt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xingheyuzhuan.shiguangschedule.Destination
import com.xingheyuzhuan.shiguangschedule.cumt.CumtAiConfig
import com.xingheyuzhuan.shiguangschedule.cumt.CumtAiCourseService
import com.xingheyuzhuan.shiguangschedule.cumt.CumtAiModelInfo
import com.xingheyuzhuan.shiguangschedule.cumt.CumtAiProtocol
import com.xingheyuzhuan.shiguangschedule.cumt.CumtAiProviderPreset
import com.xingheyuzhuan.shiguangschedule.cumt.CumtAiSessionRepository
import com.xingheyuzhuan.shiguangschedule.cumt.CumtLocalRepository
import com.xingheyuzhuan.shiguangschedule.cumt.CumtMcpAuditEntry
import com.xingheyuzhuan.shiguangschedule.cumt.CumtMcpConfig
import com.xingheyuzhuan.shiguangschedule.cumt.ShiguangMcpDispatcher
import com.xingheyuzhuan.shiguangschedule.cumt.ShiguangMcpServerManager
import com.xingheyuzhuan.shiguangschedule.tool.FileManagerCallbacks
import com.xingheyuzhuan.shiguangschedule.tool.rememberFileManager
import com.xingheyuzhuan.shiguangschedule.ui.components.ToastManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.vectorResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinViewModel
import shiguangschedule.shared.generated.resources.Res
import shiguangschedule.shared.generated.resources.award_star_24px
import shiguangschedule.shared.generated.resources.bolt_24px
import shiguangschedule.shared.generated.resources.check_24px
import shiguangschedule.shared.generated.resources.content_copy_24px
import shiguangschedule.shared.generated.resources.delete_24px
import shiguangschedule.shared.generated.resources.edit_24px
import shiguangschedule.shared.generated.resources.info_24px
import shiguangschedule.shared.generated.resources.refresh_24px
import shiguangschedule.shared.generated.resources.save_24px
import shiguangschedule.shared.generated.resources.schedule_24px
import shiguangschedule.shared.generated.resources.search_24px
import shiguangschedule.shared.generated.resources.swap_horiz_24px

@KoinViewModel
class CumtAiCourseImportViewModel(
    private val localRepo: CumtLocalRepository,
    private val aiCourseService: CumtAiCourseService,
    val sessionRepo: CumtAiSessionRepository,
    val mcpManager: ShiguangMcpServerManager,
    private val fileSystem: FileSystem,
    @Named("FilesDir") private val filesDir: Path
) : ViewModel() {

    val aiConfig: StateFlow<CumtAiConfig> = localRepo.aiConfigFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CumtAiConfig()
    )

    val cachedModels: StateFlow<List<CumtAiModelInfo>> = localRepo.cachedAiModelsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val mcpConfig: StateFlow<CumtMcpConfig> = localRepo.mcpConfigFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CumtMcpConfig()
    )

    private val _savedApiKey = MutableStateFlow("")
    val savedApiKey: StateFlow<String> = _savedApiKey.asStateFlow()

    private val _isTestingConnection = MutableStateFlow(false)
    val isTestingConnection: StateFlow<Boolean> = _isTestingConnection.asStateFlow()

    private val _testStatusMessage = MutableStateFlow<String?>(null)
    val testStatusMessage: StateFlow<String?> = _testStatusMessage.asStateFlow()

    init {
        viewModelScope.launch {
            _savedApiKey.value = localRepo.getSavedAiApiKey()
        }
    }

    fun saveApiConfig(
        providerId: String,
        baseUrl: String,
        model: String,
        protocol: CumtAiProtocol,
        apiKey: String,
        autoDiscoverModels: Boolean = true,
        onSaved: () -> Unit = {}
    ) {
        val cleanKey = apiKey.trim()
        val cleanBase = baseUrl.trim()
        val cleanModel = model.trim()
        if (cleanBase.isBlank()) {
            ToastManager.show("请填写 API Base URL")
            return
        }
        viewModelScope.launch {
            localRepo.saveAiConfig(
                providerId = providerId,
                baseUrl = cleanBase,
                model = cleanModel,
                protocol = protocol,
                apiKey = cleanKey
            )
            _savedApiKey.value = cleanKey
            _testStatusMessage.value = null
            ToastManager.show(if (cleanKey.isNotEmpty()) "API Key 配置已加密保存" else "已保存接口配置（未填 Key）")
            if (autoDiscoverModels && cleanKey.isNotEmpty()) {
                sessionRepo.refreshAvailableModels(protocol, cleanBase, cleanKey) { models ->
                    // 若用户尚未选填模型，自动选中第一个推荐模型
                    if (cleanModel.isBlank() && models.isNotEmpty()) {
                        viewModelScope.launch {
                            localRepo.saveAiConfig(
                                providerId = providerId,
                                baseUrl = cleanBase,
                                model = models.first().id,
                                protocol = protocol,
                                apiKey = cleanKey
                            )
                        }
                    }
                }
            }
            onSaved()
        }
    }

    fun clearApiKey() {
        viewModelScope.launch {
            localRepo.clearAiApiKey()
            localRepo.saveCachedAiModels(emptyList())
            _savedApiKey.value = ""
            _testStatusMessage.value = null
            ToastManager.show("已清除当前服务商保存的 API Key")
        }
    }

    fun switchProviderSlot(
        preset: CumtAiProviderPreset,
        onLoaded: (CumtAiConfig, String) -> Unit
    ) {
        viewModelScope.launch {
            val (cfg, key) = localRepo.loadOrSwitchAiProviderSlot(preset.id)
            _savedApiKey.value = key
            _testStatusMessage.value = null
            onLoaded(cfg, key)
        }
    }

    fun setFloatingOrbEnabled(enabled: Boolean) {
        viewModelScope.launch {
            localRepo.setFloatingOrbEnabled(enabled)
            ToastManager.show(if (enabled) "已开启全局 AI 悬浮小球" else "已隐藏全局 AI 悬浮小球")
        }
    }

    fun setAutoExecuteWrites(enabled: Boolean) {
        viewModelScope.launch {
            localRepo.setAiAutoExecuteWrites(enabled)
        }
    }

    fun updateOrbAppearance(
        sizeDp: Float? = null,
        idleAlpha: Float? = null,
        autoPeek: Boolean? = null,
        iconStyle: CumtAiOrbIconStyle? = null,
        quickSheetHeightFraction: Float? = null
    ) {
        viewModelScope.launch {
            localRepo.updateFloatingOrbAppearance(
                sizeDp = sizeDp,
                idleAlpha = idleAlpha,
                autoPeek = autoPeek,
                iconStyle = iconStyle,
                quickSheetHeightFraction = quickSheetHeightFraction
            )
        }
    }

    fun saveCustomOrbIcon(bitmap: ImageBitmap) {
        viewModelScope.launch {
            runCatching {
                val bytes = encodeImageBitmapForAi(bitmap, maxDimension = 512, quality = 90)
                val targetPath = filesDir.resolve("ai_orb_custom_${kotlin.time.Clock.System.now().toEpochMilliseconds()}.jpg")
                withContext(Dispatchers.IO) {
                    if (!fileSystem.exists(filesDir)) {
                        fileSystem.createDirectories(filesDir)
                    }
                    fileSystem.write(targetPath) { write(bytes) }
                }
                localRepo.updateFloatingOrbAppearance(
                    iconStyle = CumtAiOrbIconStyle.CUSTOM,
                    customImagePath = targetPath.toString()
                )
            }.fold(
                onSuccess = { ToastManager.show("已更新悬浮球自定义图标") },
                onFailure = { ToastManager.show("保存悬浮球图标失败：${it.message ?: "未知错误"}") }
            )
        }
    }

    fun testConnection(
        providerId: String,
        baseUrl: String,
        model: String,
        protocol: CumtAiProtocol,
        apiKey: String
    ) {
        if (_isTestingConnection.value) return
        viewModelScope.launch {
            _isTestingConnection.value = true
            _testStatusMessage.value = null
            val tempConfig = CumtAiConfig(
                providerId = providerId,
                baseUrl = baseUrl.trim(),
                model = model.trim(),
                protocol = protocol.name,
                hasApiKey = apiKey.isNotBlank()
            )
            aiCourseService.testConnection(tempConfig, apiKey).fold(
                onSuccess = { msg ->
                    _testStatusMessage.value = msg
                    ToastManager.show("API 连接测试成功")
                },
                onFailure = { err ->
                    val errText = err.message ?: "连接失败，请检查 Key 与网络"
                    _testStatusMessage.value = "失败：$errText"
                    ToastManager.show(errText)
                }
            )
            _isTestingConnection.value = false
        }
    }
}

@Composable
fun CumtAiCourseImportScreen(
    onNavigate: (Destination) -> Unit,
    onBack: () -> Unit,
    viewModel: CumtAiCourseImportViewModel = koinViewModel()
) {
    val aiConfig by viewModel.aiConfig.collectAsState()
    val savedApiKey by viewModel.savedApiKey.collectAsState()
    val cachedModels by viewModel.cachedModels.collectAsState()
    val mcpConfig by viewModel.mcpConfig.collectAsState()

    val messages by viewModel.sessionRepo.messages.collectAsState()
    val isBusy by viewModel.sessionRepo.isBusy.collectAsState()
    val isFetchingModels by viewModel.sessionRepo.isFetchingModels.collectAsState()
    val modelFetchNotice by viewModel.sessionRepo.modelFetchNotice.collectAsState()
    val isTestingConnection by viewModel.isTestingConnection.collectAsState()
    val testStatusMessage by viewModel.testStatusMessage.collectAsState()

    val mcpRunning by viewModel.mcpManager.isRunning.collectAsState()
    val mcpEndpoint by viewModel.mcpManager.activeEndpoint.collectAsState()
    val mcpError by viewModel.mcpManager.lastError.collectAsState()
    val lanAddresses by viewModel.mcpManager.lanAddresses.collectAsState()
    val auditLogs by viewModel.mcpManager.dispatcher.auditLogs.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    var initializedTab by remember { mutableStateOf(false) }

    LaunchedEffect(aiConfig.hasApiKey) {
        viewModel.sessionRepo.markRepliesRead()
        if (!initializedTab) {
            initializedTab = true
            // 首次进入若尚未配置 Key，自动跳到配置页方便填入
            if (!aiConfig.hasApiKey) selectedTab = 1
        }
    }

    Scaffold(
        topBar = {
            CumtTopBar(
                title = "矿大 AI 助手",
                subtitle = when (selectedTab) {
                    0 -> "${aiConfig.presetEnum.displayName} · ${aiConfig.model.ifBlank { "未选模型" }}"
                    1 -> "自动识别可用模型 · 悬浮球与 Key 设置"
                    else -> if (mcpRunning) "MCP 运行中 · $mcpEndpoint" else "MCP 本地服务（外部 Agent 读写接口）"
                },
                onBack = onBack,
                actions = {
                    if (selectedTab == 0 && messages.isNotEmpty()) {
                        IconButton(onClick = { viewModel.sessionRepo.clearConversation() }) {
                            Icon(
                                vectorResource(Res.drawable.delete_24px),
                                contentDescription = "清空对话"
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
        ) {
            CumtSegmentedBar(
                options = listOf("AI 对话", "模型与 Key", "MCP 接口"),
                selectedIndex = selectedTab,
                onSelect = { selectedTab = it },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )

            when (selectedTab) {
                0 -> AiChatTabContent(
                    aiConfig = aiConfig,
                    messages = messages,
                    isBusy = isBusy,
                    sessionRepo = viewModel.sessionRepo,
                    onJumpToSettings = { selectedTab = 1 },
                    modifier = Modifier.weight(1f)
                )
                1 -> AiModelSettingsTabContent(
                    aiConfig = aiConfig,
                    savedApiKey = savedApiKey,
                    cachedModels = cachedModels,
                    isFetchingModels = isFetchingModels,
                    modelFetchNotice = modelFetchNotice,
                    isTestingConnection = isTestingConnection,
                    testStatusMessage = testStatusMessage,
                    viewModel = viewModel,
                    onJumpToChat = { selectedTab = 0 },
                    modifier = Modifier.weight(1f)
                )
                else -> AiMcpServerTabContent(
                    mcpConfig = mcpConfig,
                    isSupported = viewModel.mcpManager.isSupported,
                    isRunning = mcpRunning,
                    activeEndpoint = mcpEndpoint,
                    lastError = mcpError,
                    lanAddresses = lanAddresses,
                    auditLogs = auditLogs,
                    mcpManager = viewModel.mcpManager,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun AiChatTabContent(
    aiConfig: CumtAiConfig,
    messages: List<com.xingheyuzhuan.shiguangschedule.cumt.CumtAiChatMessage>,
    isBusy: Boolean,
    sessionRepo: CumtAiSessionRepository,
    onJumpToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    var inputText by remember { mutableStateOf("") }
    var attachedImage by remember { mutableStateOf<ImageBitmap?>(null) }

    val fileManager = rememberFileManager(
        callbacks = FileManagerCallbacks(
            onImagePicked = { bmp ->
                if (bmp != null) attachedImage = bmp
            }
        )
    )

    Column(modifier = modifier.fillMaxSize()) {
        if (!aiConfig.hasApiKey) {
            Surface(
                onClick = onJumpToSettings,
                shape = RoundedCornerShape(14.dp),
                color = CumtAccent.AMBER.containerColor(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "尚未配置 API Key，点击前往「模型与 Key」配置并自动拉取模型",
                        style = MaterialTheme.typography.bodySmall,
                        color = CumtAccent.AMBER.contentColor(),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "去配置 ›",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = CumtAccent.AMBER.contentColor()
                    )
                }
            }
        }

        // 场景快捷指令行（克制线性图标胶囊，不堆砌彩色 Emoji）
        val chipBorder = BorderStroke(
            width = 0.8.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SuggestionChip(
                onClick = { fileManager.pickImage() },
                shape = CircleShape,
                border = chipBorder,
                icon = {
                    Icon(
                        vectorResource(Res.drawable.image_24px),
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                label = { Text("上传实验安排图") }
            )
            SuggestionChip(
                onClick = {
                    inputText = "帮我在第11周周六第1-2节加一门《二阶系统实验》，地点信控-A205，老师周晨"
                },
                shape = CircleShape,
                border = chipBorder,
                icon = {
                    Icon(
                        vectorResource(Res.drawable.edit_24px),
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                label = { Text("文字加课") }
            )
            SuggestionChip(
                onClick = {
                    inputText = "把我本周三的课程调到第13周周六5-6节"
                },
                shape = CircleShape,
                border = chipBorder,
                icon = {
                    Icon(
                        vectorResource(Res.drawable.swap_horiz_24px),
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                label = { Text("文字调课") }
            )
            SuggestionChip(
                onClick = {
                    sessionRepo.sendUserMessage(
                        text = "帮我查一下本周哪天下午没课可以约实验？",
                        contextHint = "全屏 AI 助手页面",
                        onNeedApiKey = onJumpToSettings
                    )
                },
                shape = CircleShape,
                border = chipBorder,
                icon = {
                    Icon(
                        vectorResource(Res.drawable.schedule_24px),
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                label = { Text("查本周空课") }
            )
            SuggestionChip(
                onClick = {
                    sessionRepo.sendUserMessage(
                        text = "帮我汇总一下最近的考试倒计时和加权绩点情况",
                        contextHint = "全屏 AI 助手页面",
                        onNeedApiKey = onJumpToSettings
                    )
                },
                shape = CircleShape,
                border = chipBorder,
                icon = {
                    Icon(
                        vectorResource(Res.drawable.award_star_24px),
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                label = { Text("查考表与绩点") }
            )
        }

        Box(modifier = Modifier.weight(1f)) {
            AiChatMessageList(
                messages = messages,
                isBusy = isBusy,
                emptyTitle = "矿大全能 AI 课表助手",
                emptySubtitle = "支持自然语言加课/调课/停课/删课、查空课/考试/绩点，也支持直接传实验安排表图片一键解析导入。",
                onConfirmAction = sessionRepo::confirmPendingAction,
                onRejectAction = sessionRepo::rejectPendingAction,
                onConfirmAllActions = sessionRepo::confirmAllPendingActions,
                onUndoAction = sessionRepo::undoAppliedAction
            )
        }

        AiMultimodalInputBar(
            text = inputText,
            onTextChange = { inputText = it },
            attachedImage = attachedImage,
            onPickImage = { fileManager.pickImage() },
            onRemoveImage = { attachedImage = null },
            isBusy = isBusy,
            onCancelBusy = sessionRepo::cancelCurrentTurn,
            onSend = {
                val txt = inputText
                val img = attachedImage
                inputText = ""
                attachedImage = null
                sessionRepo.sendUserMessage(
                    text = txt,
                    image = img,
                    contextHint = "全屏 AI 助手页面",
                    onNeedApiKey = onJumpToSettings
                )
            }
        )
    }
}

@Composable
private fun AiModelSettingsTabContent(
    aiConfig: CumtAiConfig,
    savedApiKey: String,
    cachedModels: List<CumtAiModelInfo>,
    isFetchingModels: Boolean,
    modelFetchNotice: String?,
    isTestingConnection: Boolean,
    testStatusMessage: String?,
    viewModel: CumtAiCourseImportViewModel,
    onJumpToChat: () -> Unit,
    modifier: Modifier = Modifier
) {
    var draftProviderId by remember { mutableStateOf(aiConfig.providerId) }
    var draftBaseUrl by remember { mutableStateOf(aiConfig.baseUrl) }
    var draftModel by remember { mutableStateOf(aiConfig.model) }
    var draftProtocol by remember { mutableStateOf(aiConfig.protocolEnum) }
    var draftApiKey by remember { mutableStateOf(savedApiKey) }
    var showApiKeyPlain by remember { mutableStateOf(false) }

    var modelQuery by remember { mutableStateOf("") }
    var visionOnlyFilter by remember { mutableStateOf(false) }

    LaunchedEffect(aiConfig.providerId, aiConfig.baseUrl, aiConfig.model, aiConfig.protocol, savedApiKey) {
        draftProviderId = aiConfig.providerId
        draftBaseUrl = aiConfig.baseUrl
        draftModel = aiConfig.model
        draftProtocol = aiConfig.protocolEnum
        draftApiKey = savedApiKey
    }

    val filteredModels = remember(cachedModels, modelQuery, visionOnlyFilter) {
        val kw = modelQuery.trim().lowercase()
        cachedModels.filter { m ->
            val matchVision = !visionOnlyFilter || m.supportsVision
            val matchKw = kw.isEmpty() || m.id.lowercase().contains(kw) || m.displayName.lowercase().contains(kw)
            matchVision && matchKw
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. 服务商与 API Key 卡
        item {
            CumtSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CumtSectionHeader(
                        title = "服务商与 API Key",
                        subtitle = "填入 Key 后点击「自动识别模型」或保存即可拉取可用模型"
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CumtAiProviderPreset.entries.forEach { preset ->
                            FilterChip(
                                selected = draftProviderId == preset.id,
                                onClick = {
                                    viewModel.switchProviderSlot(preset) { loadedCfg, loadedKey ->
                                        draftProviderId = loadedCfg.providerId
                                        draftProtocol = loadedCfg.protocolEnum
                                        draftBaseUrl = loadedCfg.baseUrl
                                        draftModel = loadedCfg.model
                                        draftApiKey = loadedKey
                                    }
                                },
                                label = { Text(preset.displayName) }
                            )
                        }
                    }

                    Text(
                        text = CumtAiProviderPreset.fromId(draftProviderId).hint,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )

                    CumtSegmentedBar(
                        options = CumtAiProtocol.entries.map { it.displayName },
                        selectedIndex = CumtAiProtocol.entries.indexOf(draftProtocol).coerceAtLeast(0),
                        onSelect = { idx -> draftProtocol = CumtAiProtocol.entries[idx] }
                    )

                    OutlinedTextField(
                        value = draftApiKey,
                        onValueChange = { draftApiKey = it },
                        label = { Text("API Key（本地加密存储）") },
                        placeholder = { Text("sk-xxxx / AIzaSy...") },
                        singleLine = true,
                        visualTransformation = if (showApiKeyPlain) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            TextButton(onClick = { showApiKeyPlain = !showApiKeyPlain }) {
                                Text(if (showApiKeyPlain) "隐藏" else "显示")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = draftBaseUrl,
                        onValueChange = { draftBaseUrl = it },
                        label = { Text("API Base URL") },
                        placeholder = { Text("https://api.siliconflow.cn/v1") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = draftModel,
                        onValueChange = { draftModel = it },
                        label = { Text("当前使用模型（可从下方自动识别列表点选）") },
                        placeholder = { Text("Qwen/Qwen2.5-VL-72B-Instruct") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (!testStatusMessage.isNullOrBlank()) {
                        val isFail = testStatusMessage.startsWith("失败")
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isFail) CumtAccent.CORAL.containerColor() else CumtAccent.EMERALD.containerColor(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = testStatusMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isFail) CumtAccent.CORAL.contentColor() else CumtAccent.EMERALD.contentColor(),
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                viewModel.testConnection(
                                    providerId = draftProviderId,
                                    baseUrl = draftBaseUrl,
                                    model = draftModel,
                                    protocol = draftProtocol,
                                    apiKey = draftApiKey
                                )
                            },
                            enabled = !isTestingConnection && draftApiKey.isNotBlank() && draftModel.isNotBlank(),
                            modifier = Modifier.weight(1f)
                        ) {
                            if (isTestingConnection) {
                                CircularProgressIndicator(modifier = Modifier.size(15.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(6.dp))
                            }
                            Text("测试连通")
                        }

                        Button(
                            onClick = {
                                viewModel.saveApiConfig(
                                    providerId = draftProviderId,
                                    baseUrl = draftBaseUrl,
                                    model = draftModel,
                                    protocol = draftProtocol,
                                    apiKey = draftApiKey,
                                    autoDiscoverModels = true
                                )
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(vectorResource(Res.drawable.save_24px), contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("保存并识模型")
                        }
                    }

                    if (aiConfig.hasApiKey) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = onJumpToChat) {
                                Text("返回对话 ›")
                            }
                            TextButton(onClick = {
                                viewModel.clearApiKey()
                                draftApiKey = ""
                            }) {
                                Text("清除 API Key", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }

        // 2. 自动识别可用模型列表
        item {
            CumtSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "自动识别可用模型（${cachedModels.size}）",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = modelFetchNotice ?: "支持从 OpenAI 兼容 /models 与 Gemini /models 自动发现",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                viewModel.sessionRepo.refreshAvailableModels(
                                    protocol = draftProtocol,
                                    baseUrl = draftBaseUrl,
                                    apiKey = draftApiKey
                                )
                            },
                            enabled = !isFetchingModels && draftApiKey.isNotBlank() && draftBaseUrl.isNotBlank()
                        ) {
                            if (isFetchingModels) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(6.dp))
                            } else {
                                Icon(vectorResource(Res.drawable.refresh_24px), contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                            }
                            Text("自动拉取")
                        }
                    }

                    if (cachedModels.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = modelQuery,
                                onValueChange = { modelQuery = it },
                                placeholder = { Text("搜索模型名...") },
                                leadingIcon = {
                                    Icon(vectorResource(Res.drawable.search_24px), contentDescription = null, modifier = Modifier.size(18.dp))
                                },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = visionOnlyFilter,
                                onClick = { visionOnlyFilter = !visionOnlyFilter },
                                label = { Text("仅视觉") }
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            filteredModels.take(40).forEach { modelInfo ->
                                val isSelected = draftModel.equals(modelInfo.id, ignoreCase = true)
                                Surface(
                                    onClick = {
                                        draftModel = modelInfo.id
                                        viewModel.saveApiConfig(
                                            providerId = draftProviderId,
                                            baseUrl = draftBaseUrl,
                                            model = modelInfo.id,
                                            protocol = draftProtocol,
                                            apiKey = draftApiKey,
                                            autoDiscoverModels = false
                                        )
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.45f)
                                    },
                                    border = if (isSelected) {
                                        BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                                    } else null,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = modelInfo.id,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(Modifier.height(3.dp))
                                            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                                if (modelInfo.supportsVision) {
                                                    CumtStatusPill(
                                                        text = "视觉识图",
                                                        containerColor = CumtAccent.VIOLET.containerColor(),
                                                        contentColor = CumtAccent.VIOLET.contentColor()
                                                    )
                                                }
                                                if (modelInfo.supportsTools) {
                                                    CumtStatusPill(
                                                        text = "工具调用",
                                                        containerColor = CumtAccent.TEAL.containerColor(),
                                                        contentColor = CumtAccent.TEAL.contentColor()
                                                    )
                                                }
                                                if (modelInfo.isReasoning) {
                                                    CumtStatusPill(
                                                        text = "深度思考",
                                                        containerColor = CumtAccent.AMBER.containerColor(),
                                                        contentColor = CumtAccent.AMBER.contentColor()
                                                    )
                                                }
                                            }
                                        }
                                        if (isSelected) {
                                            Icon(
                                                vectorResource(Res.drawable.check_24px),
                                                contentDescription = "已选中",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            if (filteredModels.size > 40) {
                                Text(
                                    text = "已展示前 40 个匹配模型，可输入关键词精准筛选",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. 悬浮球外观与交互偏好
        item {
            val orbImagePicker = rememberFileManager(
                callbacks = FileManagerCallbacks(
                    onImagePicked = { bmp ->
                        if (bmp != null) viewModel.saveCustomOrbIcon(bmp)
                    }
                )
            )
            CumtSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CumtSectionHeader(
                        title = "AI 悬浮球与弹窗外观",
                        subtitle = "自由调节悬浮球图标、大小、贴边半隐与半屏抽屉高度"
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("全局 AI 悬浮小球", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                "在课表、成绩、考试等页面边缘常驻，拖拽贴边、单击秒开半屏助手",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = aiConfig.floatingOrbEnabled,
                            onCheckedChange = viewModel::setFloatingOrbEnabled
                        )
                    }

                    if (aiConfig.floatingOrbEnabled) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                        // 实时预览条 + 图标选择
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier.size(72.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                AiOrbAvatarSurface(
                                    aiConfig = aiConfig,
                                    size = aiConfig.floatingOrbSizeDp.coerceIn(36f, 68f).dp
                                )
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("悬浮球图标风格", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    CumtAiOrbIconStyle.entries.forEach { style ->
                                        val selected = aiConfig.orbIconStyleEnum == style
                                        FilterChip(
                                            selected = selected,
                                            onClick = {
                                                if (style == CumtAiOrbIconStyle.CUSTOM && aiConfig.floatingOrbCustomImagePath.isBlank()) {
                                                    orbImagePicker.pickImage()
                                                } else {
                                                    viewModel.updateOrbAppearance(iconStyle = style)
                                                }
                                            },
                                            label = { Text(style.label) }
                                        )
                                    }
                                }
                                OutlinedButton(
                                    onClick = { orbImagePicker.pickImage() },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Icon(vectorResource(Res.drawable.image_24px), contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(Modifier.width(5.dp))
                                    Text("从相册自选图标", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }

                        // 悬浮球尺寸滑块
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("悬浮球大小", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${aiConfig.floatingOrbSizeDp.roundToInt()} dp",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Slider(
                                value = aiConfig.floatingOrbSizeDp.coerceIn(36f, 68f),
                                onValueChange = { viewModel.updateOrbAppearance(sizeDp = it.roundToInt().toFloat()) },
                                valueRange = 36f..68f
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("小巧" to 40f, "标准" to 48f, "醒目" to 58f).forEach { (label, dpVal) ->
                                    FilterChip(
                                        selected = kotlin.math.abs(aiConfig.floatingOrbSizeDp - dpVal) < 2f,
                                        onClick = { viewModel.updateOrbAppearance(sizeDp = dpVal) },
                                        label = { Text("$label ${dpVal.roundToInt()}") }
                                    )
                                }
                            }
                        }

                        // 闲置透明度滑块
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("闲置不透明度", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${(aiConfig.floatingOrbIdleAlpha * 100f).roundToInt()}%",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Slider(
                                value = aiConfig.floatingOrbIdleAlpha.coerceIn(0.35f, 1.0f),
                                onValueChange = { viewModel.updateOrbAppearance(idleAlpha = it) },
                                valueRange = 0.35f..1.0f
                            )
                        }

                        // 闲置自动半藏贴边开关
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("闲置自动贴边半隐", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "3 秒无操作自动滑入边缘一半，看课表零遮挡，轻触即弹开",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = aiConfig.floatingOrbAutoPeek,
                                onCheckedChange = { viewModel.updateOrbAppearance(autoPeek = it) }
                            )
                        }

                        // 半屏抽屉默认高度
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("悬浮球半屏抽屉高度", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf("紧凑 58%" to 0.58f, "标准 74%" to 0.74f, "大窗 90%" to 0.90f).forEach { (label, frac) ->
                                    FilterChip(
                                        selected = kotlin.math.abs(aiConfig.quickSheetHeightFraction - frac) < 0.05f,
                                        onClick = { viewModel.updateOrbAppearance(quickSheetHeightFraction = frac) },
                                        label = { Text(label) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("自动执行课表修改（信任模式）", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                "关闭时：所有新增/调课/删除均先弹出预览卡片确认后再写入（推荐）",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = aiConfig.autoExecuteWrites,
                            onCheckedChange = viewModel::setAutoExecuteWrites
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Suppress("DEPRECATION")
@Composable
private fun AiMcpServerTabContent(
    mcpConfig: CumtMcpConfig,
    isSupported: Boolean,
    isRunning: Boolean,
    activeEndpoint: String,
    lastError: String?,
    lanAddresses: List<String>,
    auditLogs: List<CumtMcpAuditEntry>,
    mcpManager: ShiguangMcpServerManager,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    var portText by remember(mcpConfig.port) { mutableStateOf(mcpConfig.port.toString()) }
    var bindAll by remember(mcpConfig.bindAllInterfaces) { mutableStateOf(mcpConfig.bindAllInterfaces) }
    var allowWrite by remember(mcpConfig.allowWrite) { mutableStateOf(mcpConfig.allowWrite) }

    val primaryHost = remember(lanAddresses, bindAll) {
        if (bindAll) lanAddresses.firstOrNull() ?: "127.0.0.1" else "127.0.0.1"
    }
    val effectivePort = portText.toIntOrNull()?.coerceIn(1024, 65535) ?: mcpConfig.port
    val configSnippet = remember(primaryHost, effectivePort, mcpConfig.token) {
        ShiguangMcpDispatcher.buildClientConfigSnippet(
            host = primaryHost,
            port = effectivePort,
            token = mcpConfig.token
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. 状态 Hero 卡片
        item {
            CumtHeroGradientCard(
                gradientColors = if (isRunning) {
                    listOf(Color(0xFF0E7C86), Color(0xFF137E52))
                } else {
                    listOf(Color(0xFF334155), Color(0xFF475569))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CumtStatusPill(
                            text = if (isRunning) "MCP 运行中" else "MCP 未启动",
                            containerColor = Color.White.copy(alpha = 0.2f),
                            contentColor = Color.White,
                            dotColor = if (isRunning) Color(0xFF7BE0B2) else Color(0xFFFFCA73)
                        )
                        CumtStatusPill(
                            text = if (allowWrite) "读写权限" else "仅只读",
                            containerColor = Color.White.copy(alpha = 0.18f),
                            contentColor = Color.White
                        )
                    }
                    Text(
                        text = if (isRunning) activeEndpoint else "对外暴露标准 MCP 课表接口",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "支持 Streamable HTTP (/mcp) 与 SSE (/sse)，让 Cursor、Claude、Antigravity、Cherry Studio 等外部 Agent 读取或修改本机课表。",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.88f)
                    )
                }
            }
        }

        // 2. 控制面板
        item {
            CumtSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (!isSupported) {
                        Text(
                            text = "当前平台暂不支持本地监听 MCP 端口，请在 Android 或桌面端开启。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = portText,
                                onValueChange = { portText = it.filter(Char::isDigit).take(5) },
                                label = { Text("监听端口") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = {
                                    if (isRunning) {
                                        mcpManager.stopServer()
                                        ToastManager.show("已停止 MCP 服务")
                                    } else {
                                        mcpManager.startOrRestart(
                                            port = effectivePort,
                                            allowWrite = allowWrite,
                                            bindAllInterfaces = bindAll
                                        )
                                        ToastManager.show("正在启动 MCP 服务...")
                                    }
                                },
                                modifier = Modifier.height(52.dp),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(vectorResource(Res.drawable.bolt_24px), contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (isRunning) "停止服务" else "启动 MCP", fontWeight = FontWeight.Bold)
                            }
                        }

                        if (!lastError.isNullOrBlank()) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = CumtAccent.CORAL.containerColor(),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "错误：$lastError",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = CumtAccent.CORAL.contentColor(),
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("允许外部 Agent 修改课表", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "关闭时仅开放查询工具；开启后允许新增、调课、修改和删除课程",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = allowWrite,
                                onCheckedChange = { checked ->
                                    allowWrite = checked
                                    mcpManager.updateAllowWrite(checked)
                                }
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("允许同局域网 / Wi-Fi 设备接入", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "监听 0.0.0.0（需重启生效），关闭则仅限本机 127.0.0.1 或 adb forward 访问",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = bindAll,
                                onCheckedChange = { checked ->
                                    bindAll = checked
                                    if (isRunning) {
                                        mcpManager.startOrRestart(effectivePort, allowWrite, checked)
                                    }
                                }
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                        // Bearer Token 区域
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("鉴权令牌 (Bearer Token)", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                Text(
                                    text = mcpConfig.token.ifBlank { "启动 MCP 服务后自动生成" },
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Row {
                                if (mcpConfig.token.isNotBlank()) {
                                    IconButton(onClick = {
                                        clipboardManager.setText(AnnotatedString(mcpConfig.token))
                                        ToastManager.show("已复制 Bearer Token")
                                    }) {
                                        Icon(vectorResource(Res.drawable.content_copy_24px), contentDescription = "复制 Token", modifier = Modifier.size(18.dp))
                                    }
                                }
                                TextButton(onClick = {
                                    mcpManager.regenerateToken()
                                    ToastManager.show("已重置鉴权 Token")
                                }) {
                                    Text("重置")
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. 一键复制标准 mcpServers 配置 JSON
        item {
            CumtSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Agent 接入配置（JSON）", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                text = "可选地址：${lanAddresses.joinToString(" / ")}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { mcpManager.refreshLanAddresses() }) {
                            Icon(vectorResource(Res.drawable.refresh_24px), contentDescription = "刷新 IP", modifier = Modifier.size(18.dp))
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SelectionContainer {
                            Text(
                                text = configSnippet,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    Button(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(configSnippet))
                            ToastManager.show("已复制 mcpServers JSON 配置")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(vectorResource(Res.drawable.content_copy_24px), contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("一键复制配置到剪贴板", fontWeight = FontWeight.Bold)
                    }

                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            vectorResource(Res.drawable.info_24px),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp).padding(top = 2.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "提示：手机与电脑在同一 Wi-Fi 下直接用局域网 IP 接入；若校园网禁止设备互通，可用数据线运行 adb forward tcp:$effectivePort tcp:$effectivePort 后改用 127.0.0.1 接入。桌面版还支持命令行参数 --mcp-stdio 直接挂载。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // 4. 实时调用审计日志
        item {
            CumtSectionHeader(
                title = "外部 Agent 调用记录（${auditLogs.size}）",
                subtitle = "实时查看外部 MCP 客户端的读写调用与权限拦截状态",
                trailing = {
                    if (auditLogs.isNotEmpty()) {
                        TextButton(onClick = { mcpManager.dispatcher.clearAuditLogs() }) {
                            Text("清空")
                        }
                    }
                }
            )
        }

        if (auditLogs.isEmpty()) {
            item {
                CumtSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "暂无外部 Agent 调用记录。启动服务并从其他 Agent 发起请求后将在此显示。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
        } else {
            items(auditLogs, key = { it.id }) { entry ->
                CumtSurfaceCard(
                    cornerRadius = 14.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CumtStatusPill(
                            text = if (entry.allowed) "允许" else "拦截",
                            containerColor = if (entry.allowed) CumtAccent.EMERALD.containerColor() else CumtAccent.CORAL.containerColor(),
                            contentColor = if (entry.allowed) CumtAccent.EMERALD.contentColor() else CumtAccent.CORAL.contentColor()
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = listOfNotNull(entry.method, entry.toolName).joinToString(" · "),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = entry.summary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}
