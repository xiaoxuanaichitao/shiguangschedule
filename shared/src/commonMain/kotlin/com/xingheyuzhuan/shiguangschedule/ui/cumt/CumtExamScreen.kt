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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xingheyuzhuan.shiguangschedule.CumtExtractMode
import com.xingheyuzhuan.shiguangschedule.Destination
import com.xingheyuzhuan.shiguangschedule.cumt.CumtExamItem
import com.xingheyuzhuan.shiguangschedule.cumt.CumtLocalRepository
import com.xingheyuzhuan.shiguangschedule.ui.components.ToastManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
import shiguangschedule.shared.generated.resources.arrow_back_24px
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
    private val localRepo: CumtLocalRepository
) : ViewModel() {
    val exams: StateFlow<List<CumtExamItem>> = localRepo.examsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun addCustomExam(courseName: String, location: String, timeDetail: String, date: LocalDate) {
        viewModelScope.launch {
            val cleanName = courseName.trim()
            if (cleanName.isEmpty()) return@launch
            val dateStr = "${date.year}-${date.month.number.toString().padStart(2, '0')}-${date.day.toString().padStart(2, '0')}"
            val rawTime = if (timeDetail.isBlank()) dateStr else "$dateStr(${timeDetail.trim()})"
            val id = "custom_${Clock.System.now().toEpochMilliseconds()}_$cleanName"
            localRepo.addCustomExam(
                CumtExamItem(
                    id = id,
                    courseName = cleanName,
                    location = location.trim(),
                    dateTimeRaw = rawTime,
                    year = date.year,
                    month = date.month.number,
                    day = date.day,
                    isCustom = true
                )
            )
            ToastManager.show("已添加「$cleanName」倒计时")
        }
    }

    fun deleteExam(id: String) {
        viewModelScope.launch {
            localRepo.deleteExam(id)
            ToastManager.show("已删除")
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            localRepo.clearAllExams()
            ToastManager.show("已清空考试列表")
        }
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
    val today = remember {
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    }

    var filterTab by remember { mutableStateOf(ExamFilterTab.UPCOMING) }
    var showAddDialog by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<CumtExamItem?>(null) }
    var confirmClearAll by remember { mutableStateOf(false) }

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
            TopAppBar(
                title = { Text("考试倒计时") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(vectorResource(Res.drawable.arrow_back_24px), contentDescription = "返回")
                    }
                },
                actions = {
                    if (exams.isNotEmpty()) {
                        IconButton(onClick = { confirmClearAll = true }) {
                            Icon(vectorResource(Res.drawable.delete_24px), contentDescription = "清空")
                        }
                    }
                    IconButton(
                        onClick = {
                            onNavigate(Destination.CumtJwxtExtract(CumtExtractMode.EXAM))
                        }
                    ) {
                        Icon(vectorResource(Res.drawable.download_24px), contentDescription = "教务导入")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(vectorResource(Res.drawable.add_24px), contentDescription = "添加自定义倒计时")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ExamFilterTab.entries.forEach { tab ->
                    FilterChip(
                        selected = filterTab == tab,
                        onClick = { filterTab = tab },
                        label = { Text(tab.label) }
                    )
                }
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = { onNavigate(Destination.CumtJwxtExtract(CumtExtractMode.EXAM)) }
                ) {
                    Text("从教务导入")
                }
            }

            if (filteredExams.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (exams.isEmpty()) "暂无考试倒计时" else "当前筛选下暂无考试",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                onClick = {
                                    onNavigate(Destination.CumtJwxtExtract(CumtExtractMode.EXAM))
                                }
                            ) {
                                Text("教务网页一键抓取")
                            }
                            OutlinedButton(onClick = { showAddDialog = true }) {
                                Text("手动添加")
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredExams, key = { it.id }) { exam ->
                        val examDate = runCatching { LocalDate(exam.year, exam.month, exam.day) }.getOrNull()
                        val daysLeft = examDate?.let { today.daysUntil(it) } ?: 0
                        ExamCountdownCard(
                            exam = exam,
                            daysLeft = daysLeft,
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
            onConfirm = { name, loc, time, date ->
                showAddDialog = false
                viewModel.addCustomExam(name, loc, time, date)
            }
        )
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除倒计时") },
            text = { Text("确定要删除「${target.courseName}」吗？") },
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
            text = { Text("确定清空全部已保存的考试与倒计时吗？") },
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
}

@Composable
private fun ExamCountdownCard(
    exam: CumtExamItem,
    daysLeft: Int,
    onDelete: () -> Unit
) {
    val isToday = daysLeft == 0
    val isFinished = daysLeft < 0
    val progress = when {
        isFinished -> 0f
        daysLeft >= 30 -> 0.1f
        else -> (1f - daysLeft / 30f).coerceIn(0.1f, 1f)
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = when {
                isToday -> MaterialTheme.colorScheme.primaryContainer
                isFinished -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                else -> MaterialTheme.colorScheme.surfaceContainerLow
            }
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = exam.courseName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (exam.isCustom) {
                            Spacer(Modifier.width(6.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = MaterialTheme.shapes.extraSmall
                            ) {
                                Text(
                                    text = "自定义",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            vectorResource(Res.drawable.schedule_24px),
                            contentDescription = null,
                            modifier = Modifier.height(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = exam.dateTimeRaw,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (exam.location.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                vectorResource(Res.drawable.location_on_24px),
                                contentDescription = null,
                                modifier = Modifier.height(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = exam.location,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = when {
                            isToday -> "就是今天"
                            isFinished -> "已结束 ${-daysLeft} 天"
                            else -> "剩 $daysLeft 天"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = when {
                            isToday -> MaterialTheme.colorScheme.primary
                            isFinished -> MaterialTheme.colorScheme.onSurfaceVariant
                            daysLeft <= 3 -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.primary
                        }
                    )
                    IconButton(onClick = onDelete) {
                        Icon(
                            vectorResource(Res.drawable.delete_24px),
                            contentDescription = "删除",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (!isFinished) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(5.dp),
                    color = if (daysLeft <= 3) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddCustomExamDialog(
    initialDate: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (name: String, location: String, timeDetail: String, date: LocalDate) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var timeDetail by remember { mutableStateOf("09:00-11:00") }
    var selectedDate by remember { mutableStateOf(initialDate) }
    var showDatePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加自定义考试/日程倒计时") },
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
                    label = { Text("时间段或备注（如 14:00-16:00）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "考试日期: ${selectedDate.year}-${selectedDate.month.number.toString().padStart(2, '0')}-${selectedDate.day.toString().padStart(2, '0')}"
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank(),
                onClick = { onConfirm(name, location, timeDetail, selectedDate) }
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
