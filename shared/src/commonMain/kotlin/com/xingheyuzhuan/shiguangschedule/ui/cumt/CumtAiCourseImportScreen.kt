package com.xingheyuzhuan.shiguangschedule.ui.cumt

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
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
import com.xingheyuzhuan.shiguangschedule.cumt.CumtAiProtocol
import com.xingheyuzhuan.shiguangschedule.cumt.CumtAiProviderPreset
import com.xingheyuzhuan.shiguangschedule.cumt.CumtAiRecognizedCourse
import com.xingheyuzhuan.shiguangschedule.cumt.CumtLocalRepository
import com.xingheyuzhuan.shiguangschedule.cumt.CumtParsers
import com.xingheyuzhuan.shiguangschedule.data.db.main.Course
import com.xingheyuzhuan.shiguangschedule.data.db.main.CourseTable
import com.xingheyuzhuan.shiguangschedule.data.repository.AppSettingsRepository
import com.xingheyuzhuan.shiguangschedule.data.repository.CourseTableRepository
import com.xingheyuzhuan.shiguangschedule.data.repository.StyleSettingsRepository
import com.xingheyuzhuan.shiguangschedule.data.sync.WidgetDataSynchronizer
import com.xingheyuzhuan.shiguangschedule.tool.FileManagerCallbacks
import com.xingheyuzhuan.shiguangschedule.tool.rememberFileManager
import com.xingheyuzhuan.shiguangschedule.ui.components.CourseTablePickerDialog
import com.xingheyuzhuan.shiguangschedule.ui.components.ToastManager
import com.xingheyuzhuan.shiguangschedule.ui.components.encodeImageBitmapForAi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.vectorResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinViewModel
import shiguangschedule.shared.generated.resources.Res
import shiguangschedule.shared.generated.resources.bolt_24px
import shiguangschedule.shared.generated.resources.check_circle_24px
import shiguangschedule.shared.generated.resources.delete_24px
import shiguangschedule.shared.generated.resources.done_all_24px
import shiguangschedule.shared.generated.resources.edit_24px
import shiguangschedule.shared.generated.resources.image_24px
import shiguangschedule.shared.generated.resources.info_24px
import shiguangschedule.shared.generated.resources.location_on_24px
import shiguangschedule.shared.generated.resources.person_24px
import shiguangschedule.shared.generated.resources.save_24px
import shiguangschedule.shared.generated.resources.schedule_24px
import shiguangschedule.shared.generated.resources.tune_24px
import shiguangschedule.shared.generated.resources.upload_24px
import kotlin.math.abs
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
@KoinViewModel
class CumtAiCourseImportViewModel(
    private val localRepo: CumtLocalRepository,
    private val aiCourseService: CumtAiCourseService,
    private val courseTableRepository: CourseTableRepository,
    private val appSettingsRepository: AppSettingsRepository,
    private val styleSettingsRepository: StyleSettingsRepository,
    private val widgetDataSynchronizer: WidgetDataSynchronizer
) : ViewModel() {

    val aiConfig: StateFlow<CumtAiConfig> = localRepo.aiConfigFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CumtAiConfig()
    )

    private val _savedApiKey = MutableStateFlow("")
    val savedApiKey: StateFlow<String> = _savedApiKey.asStateFlow()

    private val _recognizedCourses = MutableStateFlow<List<CumtAiRecognizedCourse>>(emptyList())
    val recognizedCourses: StateFlow<List<CumtAiRecognizedCourse>> = _recognizedCourses.asStateFlow()

    private val _isRecognizing = MutableStateFlow(false)
    val isRecognizing: StateFlow<Boolean> = _isRecognizing.asStateFlow()

    private val _isTestingConnection = MutableStateFlow(false)
    val isTestingConnection: StateFlow<Boolean> = _isTestingConnection.asStateFlow()

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _testStatusMessage = MutableStateFlow<String?>(null)
    val testStatusMessage: StateFlow<String?> = _testStatusMessage.asStateFlow()

    private val _addLabPrefix = MutableStateFlow(false)
    val addLabPrefix: StateFlow<Boolean> = _addLabPrefix.asStateFlow()

    init {
        viewModelScope.launch {
            _savedApiKey.value = localRepo.getSavedAiApiKey()
        }
    }

    fun setAddLabPrefix(enabled: Boolean) {
        _addLabPrefix.value = enabled
    }

    fun saveApiConfig(
        providerId: String,
        baseUrl: String,
        model: String,
        protocol: CumtAiProtocol,
        apiKey: String,
        onSaved: () -> Unit = {}
    ) {
        val cleanKey = apiKey.trim()
        val cleanBase = baseUrl.trim()
        val cleanModel = model.trim()
        if (cleanBase.isBlank()) {
            ToastManager.show("请填写 API Base URL")
            return
        }
        if (cleanModel.isBlank()) {
            ToastManager.show("请填写模型名称")
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
            onSaved()
        }
    }

    fun clearApiKey() {
        viewModelScope.launch {
            localRepo.clearAiApiKey()
            _savedApiKey.value = ""
            _testStatusMessage.value = null
            ToastManager.show("已清除本地保存的 API Key")
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

    fun recognizeImage(bitmap: ImageBitmap, userHint: String) {
        if (_isRecognizing.value) return
        viewModelScope.launch {
            val currentConfig = localRepo.aiConfigFlow.first()
            val apiKey = localRepo.getSavedAiApiKey()
            if (apiKey.isBlank()) {
                _errorMessage.value = "请先在上方配置并保存你的 AI API Key"
                ToastManager.show("请先配置并保存 API Key")
                return@launch
            }
            _isRecognizing.value = true
            _errorMessage.value = null
            try {
                val jpegBytes = encodeImageBitmapForAi(bitmap, maxDimension = 1920, quality = 85)
                aiCourseService.recognizeCoursesFromImage(
                    config = currentConfig,
                    apiKey = apiKey,
                    imageBytes = jpegBytes,
                    mimeType = "image/jpeg",
                    userHint = userHint
                ).fold(
                    onSuccess = { list ->
                        _recognizedCourses.value = list
                        ToastManager.show("AI 识别完成，共提取 ${list.size} 条课程安排")
                    },
                    onFailure = { err ->
                        val msg = err.message ?: "识别失败，请稍后重试"
                        _errorMessage.value = msg
                        ToastManager.show(msg)
                    }
                )
            } catch (e: Exception) {
                val msg = "图片处理失败：${e.message ?: "未知错误"}"
                _errorMessage.value = msg
                ToastManager.show(msg)
            } finally {
                _isRecognizing.value = false
            }
        }
    }

    fun toggleSelection(id: String) {
        _recognizedCourses.value = _recognizedCourses.value.map { item ->
            if (item.id == id) item.copy(selected = !item.selected) else item
        }
    }

    fun toggleSelectAll() {
        val list = _recognizedCourses.value
        val allSelected = list.isNotEmpty() && list.all { it.selected }
        _recognizedCourses.value = list.map { it.copy(selected = !allSelected) }
    }

    fun updateCourse(updated: CumtAiRecognizedCourse) {
        _recognizedCourses.value = _recognizedCourses.value.map { item ->
            if (item.id == updated.id) updated else item
        }
    }

    fun removeCourse(id: String) {
        _recognizedCourses.value = _recognizedCourses.value.filterNot { it.id == id }
    }

    fun clearRecognizedResults() {
        _recognizedCourses.value = emptyList()
        _errorMessage.value = null
    }

    fun appendImportToTable(
        targetTable: CourseTable,
        onImportSuccess: () -> Unit
    ) {
        if (_isImporting.value) return
        val selectedItems = _recognizedCourses.value.filter { it.selected }
        if (selectedItems.isEmpty()) {
            ToastManager.show("请至少勾选 1 条要导入的课程")
            return
        }

        viewModelScope.launch {
            _isImporting.value = true
            try {
                val existingCourses = courseTableRepository
                    .getCoursesWithWeeksByTableId(targetTable.id)
                    .first()
                val colorCount = styleSettingsRepository.styleFlow.first()
                    .courseColorMaps
                    .size
                    .coerceAtLeast(1)

                val existingNameColorMap = existingCourses.associate {
                    it.course.name to it.course.colorInt
                }
                val newNameColorMap = mutableMapOf<String, Int>()
                val prefixEnabled = _addLabPrefix.value

                var maxWeekNeeded = 0
                var hasWeekendCourse = false
                var importedCount = 0

                for (item in selectedItems) {
                    val validWeeks = item.weeks.filter { it in 1..30 }.distinct().sorted()
                    if (validWeeks.isEmpty()) continue

                    val finalName = if (prefixEnabled && !item.name.startsWith("[实验]")) {
                        "[实验] ${item.name}"
                    } else {
                        item.name
                    }

                    val colorIndex = existingNameColorMap[finalName]
                        ?: newNameColorMap.getOrPut(finalName) {
                            abs(finalName.hashCode()) % colorCount
                        }

                    val remarkText = item.remark.ifBlank { "AI 识图导入" }
                    val course = Course(
                        id = Uuid.random().toString(),
                        courseTableId = targetTable.id,
                        name = finalName,
                        teacher = item.teacher,
                        position = item.position,
                        day = item.day.coerceIn(1, 7),
                        startSection = item.startSection.coerceIn(1, 20),
                        endSection = item.endSection.coerceIn(item.startSection.coerceIn(1, 20), 20),
                        isCustomTime = false,
                        customStartTime = null,
                        customEndTime = null,
                        colorInt = colorIndex,
                        remark = remarkText,
                        credit = null
                    )

                    courseTableRepository.upsertCourse(course, validWeeks)
                    importedCount++
                    maxWeekNeeded = maxOf(maxWeekNeeded, validWeeks.max())
                    if (course.day >= 6) {
                        hasWeekendCourse = true
                    }
                }

                // 自动适配周末显示与总周数，避免周末实验课被隐藏
                val tableConfig = appSettingsRepository.getCourseConfigOnce(targetTable.id)
                var enabledWeekendNotice = false
                var expandedWeeksNotice: Int? = null
                if (tableConfig != null) {
                    val needWeekends = hasWeekendCourse && !tableConfig.showWeekends
                    val needExpandWeeks = maxWeekNeeded > tableConfig.semesterTotalWeeks
                    if (needWeekends || needExpandWeeks) {
                        appSettingsRepository.insertOrUpdateCourseConfig(
                            tableConfig.copy(
                                showWeekends = tableConfig.showWeekends || needWeekends,
                                semesterTotalWeeks = if (needExpandWeeks) maxWeekNeeded else tableConfig.semesterTotalWeeks
                            )
                        )
                        enabledWeekendNotice = needWeekends
                        if (needExpandWeeks) expandedWeeksNotice = maxWeekNeeded
                    }
                }

                widgetDataSynchronizer.syncNow()

                val extras = buildList {
                    if (enabledWeekendNotice) add("已自动开启周末显示")
                    if (expandedWeeksNotice != null) add("总周数已扩展至 $expandedWeeksNotice 周")
                }.joinToString("，")
                val suffix = if (extras.isNotEmpty()) "（$extras）" else ""
                ToastManager.show("已追加导入 $importedCount 条课程到「${targetTable.name}」$suffix")
                onImportSuccess()
            } catch (e: Exception) {
                ToastManager.show("导入失败：${e.message ?: "未知错误"}")
            } finally {
                _isImporting.value = false
            }
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
    val recognizedCourses by viewModel.recognizedCourses.collectAsState()
    val isRecognizing by viewModel.isRecognizing.collectAsState()
    val isTestingConnection by viewModel.isTestingConnection.collectAsState()
    val isImporting by viewModel.isImporting.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val testStatusMessage by viewModel.testStatusMessage.collectAsState()
    val addLabPrefix by viewModel.addLabPrefix.collectAsState()

    var selectedImage by remember { mutableStateOf<ImageBitmap?>(null) }
    var userHint by remember { mutableStateOf("") }
    var configExpanded by remember { mutableStateOf(false) }
    var initializedExpand by remember { mutableStateOf(false) }

    // 表单编辑草稿态
    var draftProviderId by remember { mutableStateOf(aiConfig.providerId) }
    var draftBaseUrl by remember { mutableStateOf(aiConfig.baseUrl) }
    var draftModel by remember { mutableStateOf(aiConfig.model) }
    var draftProtocol by remember { mutableStateOf(aiConfig.protocolEnum) }
    var draftApiKey by remember { mutableStateOf("") }
    var showApiKeyPlain by remember { mutableStateOf(false) }

    var editingCourse by remember { mutableStateOf<CumtAiRecognizedCourse?>(null) }
    var showTablePicker by remember { mutableStateOf(false) }

    LaunchedEffect(aiConfig, savedApiKey) {
        draftProviderId = aiConfig.providerId
        draftBaseUrl = aiConfig.baseUrl
        draftModel = aiConfig.model
        draftProtocol = aiConfig.protocolEnum
        draftApiKey = savedApiKey
        if (!initializedExpand) {
            configExpanded = !aiConfig.hasApiKey
            initializedExpand = true
        }
    }

    val fileManager = rememberFileManager(
        callbacks = FileManagerCallbacks(
            onImagePicked = { bitmap ->
                if (bitmap != null) {
                    selectedImage = bitmap
                    viewModel.clearRecognizedResults()
                }
            }
        )
    )

    val selectedCount = recognizedCourses.count { it.selected }

    Scaffold(
        topBar = {
            CumtTopBar(
                title = "AI 识图导实验 / 课表",
                subtitle = "自定义 API Key · 识别实验安排并追加导入",
                onBack = onBack,
                actions = {
                    TextButton(onClick = { configExpanded = !configExpanded }) {
                        Icon(
                            vectorResource(Res.drawable.tune_24px),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(if (configExpanded) "收起 Key" else "API Key")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. API Key 状态与配置卡
            item {
                CumtSurfaceCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = if (aiConfig.hasApiKey) {
                        CumtAccent.EMERALD.contentColor().copy(alpha = 0.28f)
                    } else {
                        CumtAccent.AMBER.contentColor().copy(alpha = 0.35f)
                    }
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { configExpanded = !configExpanded },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                CumtIconBadge(
                                    icon = Res.drawable.bolt_24px,
                                    accent = if (aiConfig.hasApiKey) CumtAccent.EMERALD else CumtAccent.AMBER,
                                    size = 42.dp,
                                    iconSize = 21.dp
                                )
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "AI 视觉接口配置",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        CumtStatusPill(
                                            text = if (aiConfig.hasApiKey) "已配置 Key" else "未配置 Key",
                                            containerColor = if (aiConfig.hasApiKey) {
                                                CumtAccent.EMERALD.containerColor()
                                            } else {
                                                CumtAccent.AMBER.containerColor()
                                            },
                                            contentColor = if (aiConfig.hasApiKey) {
                                                CumtAccent.EMERALD.contentColor()
                                            } else {
                                                CumtAccent.AMBER.contentColor()
                                            }
                                        )
                                    }
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = "${aiConfig.presetEnum.displayName} · ${aiConfig.model.ifBlank { "未填写模型" }}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Text(
                                text = if (configExpanded) "收起" else "配置",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        AnimatedVisibility(visible = configExpanded) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                                Text(
                                    text = "快速选择服务商预设（点击自动填充端点与视觉模型）",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CumtAiProviderPreset.entries.forEach { preset ->
                                        val selected = draftProviderId == preset.id
                                        FilterChip(
                                            selected = selected,
                                            onClick = {
                                                draftProviderId = preset.id
                                                draftProtocol = preset.protocol
                                                if (preset != CumtAiProviderPreset.CUSTOM) {
                                                    draftBaseUrl = preset.defaultBaseUrl
                                                    draftModel = preset.defaultModel
                                                }
                                            },
                                            label = { Text(preset.displayName) }
                                        )
                                    }
                                }

                                val activePreset = CumtAiProviderPreset.fromId(draftProviderId)
                                Text(
                                    text = activePreset.hint,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                CumtSegmentedBar(
                                    options = CumtAiProtocol.entries.map { it.displayName },
                                    selectedIndex = CumtAiProtocol.entries.indexOf(draftProtocol).coerceAtLeast(0),
                                    onSelect = { idx ->
                                        draftProtocol = CumtAiProtocol.entries[idx]
                                    }
                                )

                                OutlinedTextField(
                                    value = draftApiKey,
                                    onValueChange = { draftApiKey = it },
                                    label = { Text("API Key（本地加密存储，不上传第三方）") },
                                    placeholder = { Text("例如 sk-xxxx 或 AIzaSy...") },
                                    singleLine = true,
                                    visualTransformation = if (showApiKeyPlain) {
                                        VisualTransformation.None
                                    } else {
                                        PasswordVisualTransformation()
                                    },
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
                                    label = { Text("视觉模型名称 (需支持图片输入)") },
                                    placeholder = { Text("Qwen/Qwen2.5-VL-72B-Instruct") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                if (!testStatusMessage.isNullOrBlank()) {
                                    val isFail = testStatusMessage!!.startsWith("失败")
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isFail) {
                                            CumtAccent.CORAL.containerColor()
                                        } else {
                                            CumtAccent.EMERALD.containerColor()
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = testStatusMessage!!,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (isFail) {
                                                CumtAccent.CORAL.contentColor()
                                            } else {
                                                CumtAccent.EMERALD.contentColor()
                                            },
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
                                        enabled = !isTestingConnection && draftApiKey.isNotBlank(),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        if (isTestingConnection) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                strokeWidth = 2.dp
                                            )
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
                                                onSaved = {
                                                    if (draftApiKey.isNotBlank()) {
                                                        configExpanded = false
                                                    }
                                                }
                                            )
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            vectorResource(Res.drawable.save_24px),
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text("保存配置")
                                    }
                                }

                                if (aiConfig.hasApiKey) {
                                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                                        TextButton(onClick = {
                                            viewModel.clearApiKey()
                                            draftApiKey = ""
                                        }) {
                                            Text("清除已保存的 API Key", color = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 2. 选择实验安排/课表图片卡
            item {
                CumtSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CumtSectionHeader(
                            title = "上传实验安排 / 课表截图",
                            subtitle = "支持红框标注筛选，自动跳过未排时间的空行"
                        )

                        if (selectedImage != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 140.dp, max = 260.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                    .clickable { fileManager.pickImage() },
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    bitmap = selectedImage!!,
                                    contentDescription = "已选实验安排图片",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { fileManager.pickImage() },
                                    enabled = !isRecognizing,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        vectorResource(Res.drawable.image_24px),
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text("更换图片")
                                }
                            }
                        } else {
                            Surface(
                                onClick = { fileManager.pickImage() },
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f),
                                border = BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 26.dp, horizontal = 16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CumtIconBadge(
                                        icon = Res.drawable.upload_24px,
                                        accent = CumtAccent.VIOLET,
                                        size = 48.dp,
                                        iconSize = 24.dp
                                    )
                                    Text(
                                        text = "点击从相册选择实验安排图片",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "支持矿大实验系统排课截图、微信群通知表格截图等",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = userHint,
                            onValueChange = { userHint = it },
                            label = { Text("补充识别说明（可选）") },
                            placeholder = { Text("例如：只识别红框里的 / 只要第1批 / 课程名前加自动控制原理") },
                            maxLines = 2,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Button(
                            onClick = {
                                val bmp = selectedImage
                                if (bmp == null) {
                                    ToastManager.show("请先选择一张图片")
                                } else if (!aiConfig.hasApiKey) {
                                    configExpanded = true
                                    ToastManager.show("请先填写并保存 API Key")
                                } else {
                                    viewModel.recognizeImage(bmp, userHint)
                                }
                            },
                            enabled = selectedImage != null && !isRecognizing,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            if (isRecognizing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("AI 正在识别表格，请稍候...")
                            } else {
                                Icon(
                                    vectorResource(Res.drawable.bolt_24px),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = if (recognizedCourses.isEmpty()) "开始 AI 识别" else "重新识别当前图片",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        if (!errorMessage.isNullOrBlank()) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = CumtAccent.CORAL.containerColor(),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "识别未成功",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = CumtAccent.CORAL.contentColor()
                                    )
                                    Text(
                                        text = errorMessage!!,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = CumtAccent.CORAL.contentColor()
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3. 识别结果预览、编辑与追加导入
            if (recognizedCourses.isNotEmpty()) {
                item {
                    CumtSectionHeader(
                        title = "识别结果（已选 $selectedCount / ${recognizedCourses.size}）",
                        subtitle = "点条目右侧铅笔可微调，导入将追加到课表不会覆盖原有课程",
                        trailing = {
                            TextButton(onClick = viewModel::toggleSelectAll) {
                                Icon(
                                    vectorResource(Res.drawable.done_all_24px),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(if (selectedCount == recognizedCourses.size) "全不选" else "全选")
                            }
                        }
                    )
                }

                item {
                    CumtSurfaceCard(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "课名前自动添加 [实验] 标记",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "开启后更容易在周课表中与普通理论课区分",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = addLabPrefix,
                                onCheckedChange = viewModel::setAddLabPrefix
                            )
                        }
                    }
                }

                items(recognizedCourses, key = { it.id }) { item ->
                    AiRecognizedCourseCard(
                        item = item,
                        showLabPrefix = addLabPrefix,
                        onToggleSelect = { viewModel.toggleSelection(item.id) },
                        onEdit = { editingCourse = item },
                        onDelete = { viewModel.removeCourse(item.id) }
                    )
                }

                item {
                    Button(
                        onClick = { showTablePicker = true },
                        enabled = selectedCount > 0 && !isImporting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        if (isImporting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("正在追加写入课表...")
                        } else {
                            Icon(
                                vectorResource(Res.drawable.check_circle_24px),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "追加导入已选 $selectedCount 条到课表",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // 4. 隐私与使用提示
            item {
                CumtSurfaceCard(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        CumtIconBadge(
                            icon = Res.drawable.info_24px,
                            accent = CumtAccent.SLATE,
                            size = 36.dp,
                            iconSize = 18.dp
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "说明：本功能为「追加导入」，不会清空当前课表中的常规课程；若包含周六/周日实验课，导入时会自动为您开启课表周末显示。您的 API Key 仅加密保存在本地设备，识别请求直连您配置的模型接口。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    if (editingCourse != null) {
        EditRecognizedCourseDialog(
            course = editingCourse!!,
            onDismiss = { editingCourse = null },
            onSave = { updated ->
                viewModel.updateCourse(updated)
                editingCourse = null
            }
        )
    }

    if (showTablePicker) {
        CourseTablePickerDialog(
            title = "选择要追加导入的目标课表",
            onDismissRequest = { showTablePicker = false },
            onTableSelected = { table ->
                showTablePicker = false
                viewModel.appendImportToTable(table) {
                    onNavigate(Destination.CourseSchedule)
                }
            }
        )
    }
}

@Composable
private fun AiRecognizedCourseCard(
    item: CumtAiRecognizedCourse,
    showLabPrefix: Boolean,
    onToggleSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val displayName = if (showLabPrefix && !item.name.startsWith("[实验]")) {
        "[实验] ${item.name}"
    } else {
        item.name
    }
    val weeksText = "第${CumtParsers.formatWeeksCompact(item.weeks)}周"
    val dayText = CumtParsers.formatDayOfWeekLabel(item.day)
    val sectionText = if (item.startSection == item.endSection) {
        "第${item.startSection}节"
    } else {
        "第${item.startSection}-${item.endSection}节"
    }

    CumtSurfaceCard(
        onClick = onToggleSelect,
        borderColor = if (item.selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
        } else {
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Checkbox(
                checked = item.selected,
                onCheckedChange = { onToggleSelect() }
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CumtStatusPill(
                        text = weeksText,
                        containerColor = CumtAccent.INDIGO.containerColor(),
                        contentColor = CumtAccent.INDIGO.contentColor()
                    )
                    CumtStatusPill(
                        text = "$dayText · $sectionText",
                        containerColor = CumtAccent.TEAL.containerColor(),
                        contentColor = CumtAccent.TEAL.contentColor(),
                        icon = Res.drawable.schedule_24px
                    )
                }
                val metaParts = buildList {
                    if (item.position.isNotBlank()) add(item.position)
                    if (item.teacher.isNotBlank()) add(item.teacher)
                }
                if (metaParts.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (item.position.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    vectorResource(Res.drawable.location_on_24px),
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.width(3.dp))
                                Text(
                                    text = item.position,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (item.teacher.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    vectorResource(Res.drawable.person_24px),
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.width(3.dp))
                                Text(
                                    text = item.teacher,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
                if (item.remark.isNotBlank()) {
                    Text(
                        text = item.remark,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = onEdit, modifier = Modifier.size(34.dp)) {
                    Icon(
                        vectorResource(Res.drawable.edit_24px),
                        contentDescription = "编辑",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                    Icon(
                        vectorResource(Res.drawable.delete_24px),
                        contentDescription = "移除",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun EditRecognizedCourseDialog(
    course: CumtAiRecognizedCourse,
    onDismiss: () -> Unit,
    onSave: (CumtAiRecognizedCourse) -> Unit
) {
    var name by remember { mutableStateOf(course.name) }
    var teacher by remember { mutableStateOf(course.teacher) }
    var position by remember { mutableStateOf(course.position) }
    var day by remember { mutableIntStateOf(course.day.coerceIn(1, 7)) }
    var startSecText by remember { mutableStateOf(course.startSection.toString()) }
    var endSecText by remember { mutableStateOf(course.endSection.toString()) }
    var weeksText by remember { mutableStateOf(CumtParsers.formatWeeksCompact(course.weeks)) }
    var remark by remember { mutableStateOf(course.remark) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑识别条目", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("课程 / 实验名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = teacher,
                        onValueChange = { teacher = it },
                        label = { Text("教师") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = position,
                        onValueChange = { position = it },
                        label = { Text("地点/实验室") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Text(
                    text = "星期",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    (1..7).forEach { d ->
                        FilterChip(
                            selected = day == d,
                            onClick = { day = d },
                            label = { Text(CumtParsers.formatDayOfWeekLabel(d)) }
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = startSecText,
                        onValueChange = { startSecText = it.filter(Char::isDigit) },
                        label = { Text("起始节次") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = endSecText,
                        onValueChange = { endSecText = it.filter(Char::isDigit) },
                        label = { Text("结束节次") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = weeksText,
                    onValueChange = { weeksText = it },
                    label = { Text("周次（如 11 或 11-12 或 11,13）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = remark,
                    onValueChange = { remark = it },
                    label = { Text("备注（批次/学时等）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmedName = name.trim()
                    val parsedWeeks = CumtParsers.parseManualWeeksInput(weeksText)
                    val s = startSecText.toIntOrNull()?.coerceIn(1, 20)
                    val e = endSecText.toIntOrNull()?.coerceIn(1, 20)
                    if (trimmedName.isBlank()) {
                        ToastManager.show("名称不能为空")
                        return@Button
                    }
                    if (s == null || e == null || e < s) {
                        ToastManager.show("请输入有效的起止节次（如 1 到 2）")
                        return@Button
                    }
                    if (parsedWeeks.isEmpty()) {
                        ToastManager.show("请输入有效周次（1~30）")
                        return@Button
                    }
                    onSave(
                        course.copy(
                            name = trimmedName,
                            teacher = teacher.trim(),
                            position = position.trim(),
                            day = day,
                            startSection = s,
                            endSection = e,
                            weeks = parsedWeeks,
                            remark = remark.trim()
                        )
                    )
                }
            ) {
                Text("保存修改")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
