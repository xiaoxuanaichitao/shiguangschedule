package com.xingheyuzhuan.shiguangschedule.ui.cumt

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xingheyuzhuan.shiguangschedule.CumtExtractMode
import com.xingheyuzhuan.shiguangschedule.Destination
import com.xingheyuzhuan.shiguangschedule.cumt.CumtExamItem
import com.xingheyuzhuan.shiguangschedule.cumt.CumtExamScheduleSynchronizer
import com.xingheyuzhuan.shiguangschedule.cumt.CumtLocalRepository
import com.xingheyuzhuan.shiguangschedule.cumt.CumtParsers
import com.xingheyuzhuan.shiguangschedule.cumt.ExamSyncReport
import com.xingheyuzhuan.shiguangschedule.ui.components.ToastManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.vectorResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinViewModel
import shiguangschedule.shared.generated.resources.Res
import shiguangschedule.shared.generated.resources.add_24px
import shiguangschedule.shared.generated.resources.calendar_today_24px
import shiguangschedule.shared.generated.resources.check_circle_24px
import shiguangschedule.shared.generated.resources.delete_24px
import shiguangschedule.shared.generated.resources.download_24px
import shiguangschedule.shared.generated.resources.location_on_24px
import shiguangschedule.shared.generated.resources.schedule_24px
import kotlin.time.Clock
import kotlin.time.Instant

enum class ExamFilterTab(val label: String) {
    UPCOMING("待考"),
    ALL("全部"),
    FINISHED("已结束")
}

@KoinViewModel
class CumtExamViewModel(
    private val localRepo: CumtLocalRepository,
    private val examScheduleSynchronizer: CumtExamScheduleSynchronizer
) : ViewModel() {
    val exams: StateFlow<List<CumtExamItem>> = localRepo.examsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _missingSemesterStartDialog = MutableStateFlow(false)
    val missingSemesterStartDialog: StateFlow<Boolean> = _missingSemesterStartDialog.asStateFlow()

    fun dismissMissingSemesterDialog() {
        _missingSemesterStartDialog.value = false
    }

    fun addCustomExam(
        courseName: String,
        location: String,
        timeDetail: String,
        date: LocalDate,
        alsoSyncToSchedule: Boolean
    ) {
        viewModelScope.launch {
            val cleanName = courseName.trim()
            if (cleanName.isEmpty()) return@launch
            val dateStr = "${date.year}-${date.month.number.toString().padStart(2, '0')}-${date.day.toString().padStart(2, '0')}"
            val rawTime = if (timeDetail.isBlank()) dateStr else "$dateStr(${timeDetail.trim()})"
            val timeRange = CumtParsers.parseExamTimeRange(timeDetail)
            if (alsoSyncToSchedule && timeRange == null) {
                ToastManager.show("未识别到有效起止时间（格式如 09:00-11:00），已仅添加倒计时")
            }

            val id = "custom_${Clock.System.now().toEpochMilliseconds()}_$cleanName"
            val canSync = alsoSyncToSchedule && timeRange != null
            localRepo.addCustomExam(
                CumtExamItem(
                    id = id,
                    courseName = cleanName,
                    location = location.trim(),
                    dateTimeRaw = rawTime,
                    year = date.year,
                    month = date.month.number,
                    day = date.day,
                    isCustom = true,
                    startTime = timeRange?.first,
                    endTime = timeRange?.second,
                    syncedToSchedule = canSync
                )
            )
            if (canSync) {
                val report = examScheduleSynchronizer.syncEnabledExamsToTable()
                handleSyncReport(report, prefix = "已添加并插入课表")
            } else {
                ToastManager.show("已添加「$cleanName」倒计时")
            }
        }
    }

    fun syncAllUpcomingToSchedule() {
        viewModelScope.launch {
            val report = examScheduleSynchronizer.syncAllUpcomingToCurrentTable()
            handleSyncReport(report, prefix = "考试已同步到当前课表")
        }
    }

    fun toggleExamInSchedule(exam: CumtExamItem) {
        viewModelScope.launch {
            val willSync = !exam.syncedToSchedule
            val report = examScheduleSynchronizer.toggleSingleExamInSchedule(exam)
            if (report.errorMessage != null) {
                if (report.errorMessage.contains("开学日期")) {
                    _missingSemesterStartDialog.value = true
                }
                ToastManager.show(report.errorMessage)
            } else if (willSync && report.skippedOutOfRangeCount > 0) {
                ToastManager.show("该考试日期不在当前学期 1~30 周范围内，未能插入课表")
            } else {
                ToastManager.show(if (willSync) "已将「${exam.courseName}」插入课表" else "已从课表移出「${exam.courseName}」")
            }
        }
    }

    fun removeAllFromSchedule() {
        viewModelScope.launch {
            val count = examScheduleSynchronizer.removeAllSyncedExamsFromCurrentTable()
            ToastManager.show(if (count > 0) "已从课表撤回 $count 场考试" else "当前课表中没有已同步的考试")
        }
    }

    fun deleteExam(id: String) {
        viewModelScope.launch {
            examScheduleSynchronizer.deleteExamAndCleanupSchedule(id)
            ToastManager.show("已删除")
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            examScheduleSynchronizer.removeAllSyncedExamsFromCurrentTable()
            localRepo.clearAllExams()
            ToastManager.show("已清空全部考试记录")
        }
    }

    private fun handleSyncReport(report: ExamSyncReport, prefix: String) {
        if (report.errorMessage != null) {
            if (report.errorMessage.contains("开学日期")) {
                _missingSemesterStartDialog.value = true
            }
            ToastManager.show(report.errorMessage)
            return
        }
        val notes = buildList {
            if (report.enabledWeekends) add("已自动开启显示周末")
            if (report.expandedWeeksTo != null) add("总周数已扩展至第 ${report.expandedWeeksTo} 周")
            if (report.skippedNoTimeCount > 0) add("跳过 ${report.skippedNoTimeCount} 条无时间段记录")
            if (report.skippedOutOfRangeCount > 0) add("${report.skippedOutOfRangeCount} 条超出学期范围")
        }
        val suffix = if (notes.isNotEmpty()) "（${notes.joinToString("，")}）" else ""
        ToastManager.show("$prefix：共 ${report.syncedCount} 场$suffix")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CumtExamScreen(
    onNavigate: (Destination) -> Unit,
    onBack: () -> Unit,
    viewModel: CumtExamViewModel = koinViewModel()
) {
    val exams by viewModel.exams.collectAsState()
    val showMissingStartDialog by viewModel.missingSemesterStartDialog.collectAsState()
    val today = remember {
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    }

    var filterIndex by remember { mutableIntStateOf(0) }
    val filterTab = ExamFilterTab.entries.getOrElse(filterIndex) { ExamFilterTab.UPCOMING }
    var showAddDialog by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<CumtExamItem?>(null) }
    var confirmClearAll by remember { mutableStateOf(false) }

    val upcomingList = remember(exams, today) {
        exams.mapNotNull { exam ->
            val d = runCatching { LocalDate(exam.year, exam.month, exam.day) }.getOrNull() ?: return@mapNotNull null
            val diff = today.daysUntil(d)
            if (diff >= 0) exam to diff else null
        }.sortedBy { it.second }
    }
    val syncedCount = remember(exams) { exams.count { it.syncedToSchedule } }

    val filteredExams = remember(exams, filterTab, today) {
        exams.filter { exam ->
            val examDate = runCatching { LocalDate(exam.year, exam.month, exam.day) }.getOrNull()
                ?: return@filter false
            val diff = today.daysUntil(examDate)
            when (filterTab) {
                ExamFilterTab.UPCOMING -> diff >= 0
                ExamFilterTab.FINISHED -> diff < 0
                ExamFilterTab.ALL -> true
            }
        }
    }

    Scaffold(
        topBar = {
            CumtTopBar(
                title = "考试倒计时",
                subtitle = "支持抓考表与一键插入课表提醒",
                onBack = onBack,
                actions = {
                    if (exams.isNotEmpty()) {
                        IconButton(onClick = { confirmClearAll = true }) {
                            Icon(vectorResource(Res.drawable.delete_24px), contentDescription = "清空")
                        }
                    }
                    IconButton(
                        onClick = { onNavigate(Destination.CumtJwxtExtract(CumtExtractMode.EXAM)) }
                    ) {
                        Icon(vectorResource(Res.drawable.download_24px), contentDescription = "教务导入")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                shape = RoundedCornerShape(18.dp)
            ) {
                Icon(vectorResource(Res.drawable.add_24px), contentDescription = "添加自定义倒计时")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 顶部考试概览与课表同步 Hero 卡片
            CumtHeroGradientCard(
                gradientColors = listOf(Color(0xFFD9534F), Color(0xFFE07A3A)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (upcomingList.isEmpty()) "暂无待考科目" else "待考 ${upcomingList.size} 场考试",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                            val nearest = upcomingList.firstOrNull()
                            Text(
                                text = if (nearest != null) {
                                    val (e, d) = nearest
                                    val dayTip = if (d == 0) "今天开考" else "距下一场 ${d} 天"
                                    "$dayTip · ${e.courseName}"
                                } else {
                                    "可从正方教务抓取考表或手动新增重要考试"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.9f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        CumtStatusPill(
                            text = "已入课表 $syncedCount",
                            containerColor = Color.White.copy(alpha = 0.22f),
                            contentColor = Color.White
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = viewModel::syncAllUpcomingToSchedule,
                            enabled = upcomingList.isNotEmpty(),
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = Color(0xFFC83E2B)
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                vectorResource(Res.drawable.calendar_today_24px),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("一键同步到课表", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        }

                        if (syncedCount > 0) {
                            OutlinedButton(
                                onClick = viewModel::removeAllFromSchedule,
                                shape = RoundedCornerShape(50),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("撤回课表考试", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }

            // 分段筛选条 + 抓取快捷入口
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CumtSegmentedBar(
                    options = ExamFilterTab.entries.map { it.label },
                    selectedIndex = filterIndex,
                    onSelect = { filterIndex = it },
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(
                    onClick = { onNavigate(Destination.CumtJwxtExtract(CumtExtractMode.EXAM)) },
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 9.dp)
                ) {
                    Text("教务抓取", style = MaterialTheme.typography.labelLarge)
                }
            }

            if (filteredExams.isEmpty()) {
                CumtEmptyState(
                    icon = Res.drawable.schedule_24px,
                    accent = CumtAccent.CORAL,
                    title = if (exams.isEmpty()) "暂无考试安排" else "当前筛选下暂无考试",
                    subtitle = "登录本科正方教务自动抓取考表，并一键同步进周课表开启考前免打扰与通知提醒。",
                    primaryActionText = "教务网页抓取",
                    onPrimaryAction = { onNavigate(Destination.CumtJwxtExtract(CumtExtractMode.EXAM)) },
                    secondaryActionText = "手动添加",
                    onSecondaryAction = { showAddDialog = true }
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredExams, key = { it.id }) { exam ->
                        val examDate = runCatching { LocalDate(exam.year, exam.month, exam.day) }.getOrNull()
                        val daysLeft = examDate?.let { today.daysUntil(it) } ?: 0
                        ExamCountdownCard(
                            exam = exam,
                            daysLeft = daysLeft,
                            onToggleSchedule = { viewModel.toggleExamInSchedule(exam) },
                            onDelete = { pendingDelete = exam }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddCustomExamDialog(
            initialDate = today,
            onDismiss = { showAddDialog = false },
            onConfirm = { name, loc, time, date, alsoSync ->
                showAddDialog = false
                viewModel.addCustomExam(name, loc, time, date, alsoSync)
            }
        )
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除倒计时") },
            text = { Text("确定要删除「${target.courseName}」吗？若已同步进课表也会一并移除。") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteExam(target.id)
                        pendingDelete = null
                    }
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            }
        )
    }

    if (confirmClearAll) {
        AlertDialog(
            onDismissRequest = { confirmClearAll = false },
            title = { Text("清空考试记录") },
            text = { Text("确定清空全部已保存的考试记录，并从当前课表移除已插入的考试吗？") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAll()
                        confirmClearAll = false
                    }
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearAll = false }) { Text("取消") }
            }
        )
    }

    if (showMissingStartDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissMissingSemesterDialog,
            title = { Text("需要设置开学日期") },
            text = { Text("将考试日期换算为周次并插入课表需要当前课表的开学日期。是否前往「我的」设置开学日期？") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.dismissMissingSemesterDialog()
                        onNavigate(Destination.Settings)
                    }
                ) { Text("前往设置") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissMissingSemesterDialog) { Text("稍后再说") }
            }
        )
    }
}

@Composable
private fun ExamCountdownCard(
    exam: CumtExamItem,
    daysLeft: Int,
    onToggleSchedule: () -> Unit,
    onDelete: () -> Unit
) {
    val isToday = daysLeft == 0
    val isFinished = daysLeft < 0
    val progress = when {
        isFinished -> 0f
        daysLeft >= 30 -> 0.1f
        else -> (1f - daysLeft / 30f).coerceIn(0.1f, 1f)
    }

    val badgeAccent = when {
        isFinished -> CumtAccent.SLATE
        isToday || daysLeft <= 3 -> CumtAccent.CORAL
        daysLeft <= 10 -> CumtAccent.AMBER
        else -> CumtAccent.OCEAN
    }

    CumtSurfaceCard(
        containerColor = if (isFinished) {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左侧大号倒计时方块
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = badgeAccent.containerColor(),
                    modifier = Modifier.size(width = 66.dp, height = 68.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = when {
                                isToday -> "今天"
                                isFinished -> "${-daysLeft}"
                                else -> "$daysLeft"
                            },
                            style = if (isToday) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = badgeAccent.contentColor()
                        )
                        if (!isToday) {
                            Text(
                                text = if (isFinished) "天前" else "天后",
                                style = MaterialTheme.typography.labelSmall,
                                color = badgeAccent.contentColor().copy(alpha = 0.85f)
                            )
                        }
                    }
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = exam.courseName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (exam.isCustom) {
                            CumtStatusPill(
                                text = "自定义",
                                containerColor = CumtAccent.VIOLET.containerColor(),
                                contentColor = CumtAccent.VIOLET.contentColor()
                            )
                        }
                    }
                    Spacer(Modifier.height(5.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            vectorResource(Res.drawable.schedule_24px),
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = exam.dateTimeRaw,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (exam.location.isNotBlank() || exam.seatNumber.isNotBlank()) {
                        Spacer(Modifier.height(3.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                vectorResource(Res.drawable.location_on_24px),
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(4.dp))
                            val placeText = buildString {
                                append(exam.location.ifBlank { "考场待定" })
                                if (exam.seatNumber.isNotBlank()) {
                                    append(" · 座位 ${exam.seatNumber}")
                                }
                            }
                            Text(
                                text = placeText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                IconButton(onClick = onDelete) {
                    Icon(
                        vectorResource(Res.drawable.delete_24px),
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (!isFinished) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = badgeAccent.contentColor(),
                        trackColor = badgeAccent.containerColor(),
                        strokeCap = StrokeCap.Round
                    )

                    val synced = exam.syncedToSchedule
                    OutlinedButton(
                        onClick = onToggleSchedule,
                        shape = RoundedCornerShape(50),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                    ) {
                        if (synced) {
                            Icon(
                                vectorResource(Res.drawable.check_circle_24px),
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = CumtAccent.EMERALD.contentColor()
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "已入课表",
                                style = MaterialTheme.typography.labelSmall,
                                color = CumtAccent.EMERALD.contentColor()
                            )
                        } else {
                            Text(
                                text = "加入课表",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddCustomExamDialog(
    initialDate: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (name: String, location: String, timeDetail: String, date: LocalDate, alsoSyncToSchedule: Boolean) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var timeDetail by remember { mutableStateOf("09:00-11:00") }
    var selectedDate by remember { mutableStateOf(initialDate) }
    var alsoSyncToSchedule by remember { mutableStateOf(true) }
    var showDatePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加自定义考试倒计时") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("科目或事项名称*") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("考场 / 地点") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = timeDetail,
                    onValueChange = { timeDetail = it },
                    label = { Text("时间段（如 14:00-16:00，同步课表必填）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "考试日期: ${selectedDate.year}-${selectedDate.month.number.toString().padStart(2, '0')}-${selectedDate.day.toString().padStart(2, '0')}"
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("同时插入当前课表", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(
                            "可在周课表/今日课表/考前提醒中展示",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = alsoSyncToSchedule, onCheckedChange = { alsoSyncToSchedule = it })
                }
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank(),
                onClick = { onConfirm(name, location, timeDetail, selectedDate, alsoSyncToSchedule) }
            ) { Text("添加") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            selectedDate = Instant.fromEpochMilliseconds(millis)
                                .toLocalDateTime(TimeZone.UTC).date
                        }
                        showDatePicker = false
                    }
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
