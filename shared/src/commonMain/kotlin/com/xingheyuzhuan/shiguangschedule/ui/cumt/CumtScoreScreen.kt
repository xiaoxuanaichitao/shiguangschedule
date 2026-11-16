package com.xingheyuzhuan.shiguangschedule.ui.cumt

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xingheyuzhuan.shiguangschedule.CumtExtractMode
import com.xingheyuzhuan.shiguangschedule.Destination
import com.xingheyuzhuan.shiguangschedule.cumt.CumtConstants
import com.xingheyuzhuan.shiguangschedule.cumt.CumtLocalRepository
import com.xingheyuzhuan.shiguangschedule.cumt.CumtParsers
import com.xingheyuzhuan.shiguangschedule.cumt.CumtScoreItem
import com.xingheyuzhuan.shiguangschedule.cumt.CumtScoreMappingEntry
import com.xingheyuzhuan.shiguangschedule.cumt.ScoreSortOption
import com.xingheyuzhuan.shiguangschedule.ui.components.ToastManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.vectorResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinViewModel
import shiguangschedule.shared.generated.resources.Res
import shiguangschedule.shared.generated.resources.add_24px
import shiguangschedule.shared.generated.resources.arrow_back_24px
import shiguangschedule.shared.generated.resources.delete_24px
import shiguangschedule.shared.generated.resources.download_24px
import shiguangschedule.shared.generated.resources.info_24px
import shiguangschedule.shared.generated.resources.refresh_24px
import shiguangschedule.shared.generated.resources.search_24px
import shiguangschedule.shared.generated.resources.tune_24px
import kotlin.math.round

@KoinViewModel
class CumtScoreViewModel(
    private val localRepo: CumtLocalRepository
) : ViewModel() {

    val scores: StateFlow<List<CumtScoreItem>> = localRepo.scoresFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val mappings: StateFlow<List<CumtScoreMappingEntry>> = localRepo.scoreMappingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val rate: StateFlow<Double> = localRepo.scoreRateFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 1.0
    )

    fun toggleCourseIncluded(itemId: String) {
        viewModelScope.launch {
            val updated = scores.value.map { item ->
                if (item.id == itemId) item.copy(isIncluded = !item.isIncluded) else item
            }
            localRepo.saveScores(updated)
        }
    }

    fun selectAll(included: Boolean) {
        viewModelScope.launch {
            val updated = scores.value.map { it.copy(isIncluded = included) }
            localRepo.saveScores(updated)
        }
    }

    fun setRate(newRate: Double) {
        if (newRate <= 0.0 || newRate > 5.0) {
            ToastManager.show("倍率范围应在 0.01 ~ 5.0 之间")
            return
        }
        viewModelScope.launch {
            localRepo.saveScoreRate(newRate)
        }
    }

    fun saveMapping(entry: CumtScoreMappingEntry) {
        val cleanName = entry.rawName.trim()
        if (cleanName.isEmpty()) return
        viewModelScope.launch {
            val list = mappings.value.filterNot { it.rawName == cleanName } + entry.copy(rawName = cleanName)
            localRepo.saveScoreMappings(list)
            ToastManager.show("已保存「$cleanName」映射")
        }
    }

    fun deleteMapping(rawName: String) {
        viewModelScope.launch {
            val list = mappings.value.filterNot { it.rawName == rawName }
            localRepo.saveScoreMappings(list)
        }
    }

    fun resetMappings() {
        viewModelScope.launch {
            localRepo.resetScoreMappings()
            ToastManager.show("已恢复默认等级换算规则")
        }
    }
}

private fun formatTwoDecimals(value: Double): String {
    val scaled = round(value * 100.0).toLong()
    val whole = scaled / 100
    val frac = kotlin.math.abs(scaled % 100).toString().padStart(2, '0')
    return "$whole.$frac"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CumtScoreScreen(
    onNavigate: (Destination) -> Unit,
    onBack: () -> Unit,
    viewModel: CumtScoreViewModel = koinViewModel()
) {
    val scores by viewModel.scores.collectAsState()
    val mappings by viewModel.mappings.collectAsState()
    val rate by viewModel.rate.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var sortOption by remember { mutableStateOf(ScoreSortOption.DEFAULT) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showRateDialog by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }

    val summary = remember(scores, rate, mappings) {
        CumtParsers.calculateScoreSummary(scores, rate, mappings)
    }

    val displayedScores = remember(scores, searchQuery, sortOption, mappings) {
        val filtered = if (searchQuery.isBlank()) {
            scores
        } else {
            val q = searchQuery.trim()
            scores.filter {
                it.courseName.contains(q, ignoreCase = true) || it.scoreRaw.contains(q, ignoreCase = true)
            }
        }
        when (sortOption) {
            ScoreSortOption.DEFAULT -> filtered
            ScoreSortOption.NAME_ASC -> filtered.sortedBy { it.courseName }
            ScoreSortOption.NAME_DESC -> filtered.sortedByDescending { it.courseName }
            ScoreSortOption.SCORE_DESC -> filtered.sortedByDescending {
                CumtParsers.resolveEffectiveScoreAndGpa(it, mappings)?.first ?: -1.0
            }
            ScoreSortOption.SCORE_ASC -> filtered.sortedBy {
                CumtParsers.resolveEffectiveScoreAndGpa(it, mappings)?.first ?: -1.0
            }
            ScoreSortOption.CREDIT_DESC -> filtered.sortedByDescending { it.credit }
            ScoreSortOption.CREDIT_ASC -> filtered.sortedBy { it.credit }
        }
    }

    val allIncluded = scores.isNotEmpty() && scores.all { it.isIncluded }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("成绩查询与绩点") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(vectorResource(Res.drawable.arrow_back_24px), contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showHelpDialog = true }) {
                        Icon(vectorResource(Res.drawable.info_24px), contentDescription = "说明")
                    }
                    IconButton(onClick = { onNavigate(Destination.CumtScoreMappings) }) {
                        Icon(vectorResource(Res.drawable.tune_24px), contentDescription = "等级分设置")
                    }
                    IconButton(
                        onClick = { onNavigate(Destination.CumtJwxtExtract(CumtExtractMode.SCORE)) }
                    ) {
                        Icon(vectorResource(Res.drawable.download_24px), contentDescription = "导入成绩")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 顶部汇总卡片
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "加权平均绩点 (GPA)",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                            Text(
                                text = formatTwoDecimals(summary.weightedGpa),
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "加权平均成绩",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                            Text(
                                text = formatTwoDecimals(summary.weightedScore),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "已纳入 ${summary.includedCount} 门 · 共 ${formatTwoDecimals(summary.totalCredits)} 学分 · 倍率 ${formatTwoDecimals(rate)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                        )
                    }
                }
            }

            // 筛选与快捷操作栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = rate == 1.0,
                    onClick = { viewModel.setRate(1.0) },
                    label = { Text("1.0x") }
                )
                FilterChip(
                    selected = kotlin.math.abs(rate - 1.2) < 0.001,
                    onClick = { viewModel.setRate(1.2) },
                    label = { Text("1.2x") }
                )
                FilterChip(
                    selected = rate != 1.0 && kotlin.math.abs(rate - 1.2) >= 0.001,
                    onClick = { showRateDialog = true },
                    label = { Text("自定义倍率") }
                )
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = {
                        onNavigate(Destination.WebView(CumtConstants.JWXT_SCORE_DETAIL_URL, null))
                    }
                ) {
                    Text("分项明细")
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("搜索课程或成绩...") },
                    leadingIcon = {
                        Icon(vectorResource(Res.drawable.search_24px), contentDescription = null)
                    },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )

                Box {
                    OutlinedButton(onClick = { showSortMenu = true }) {
                        Text("排序")
                    }
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false }
                    ) {
                        ScoreSortOption.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.displayName) },
                                onClick = {
                                    sortOption = option
                                    showSortMenu = false
                                }
                            )
                        }
                    }
                }

                if (scores.isNotEmpty()) {
                    TextButton(onClick = { viewModel.selectAll(!allIncluded) }) {
                        Text(if (allIncluded) "全不选" else "全选")
                    }
                }
            }

            if (displayedScores.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (scores.isEmpty()) "暂未导入课程成绩" else "没有匹配的成绩记录",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = {
                                onNavigate(Destination.CumtJwxtExtract(CumtExtractMode.SCORE))
                            }
                        ) {
                            Text("前往正方教务抓取成绩")
                        }
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(displayedScores, key = { it.id }) { item ->
                        ScoreRowCard(
                            item = item,
                            mappings = mappings,
                            onToggle = { viewModel.toggleCourseIncluded(item.id) }
                        )
                    }
                }
            }
        }
    }

    if (showRateDialog) {
        var rateInput by remember { mutableStateOf(formatTwoDecimals(rate)) }
        AlertDialog(
            onDismissRequest = { showRateDialog = false },
            title = { Text("自定义计算倍率") },
            text = {
                OutlinedTextField(
                    value = rateInput,
                    onValueChange = { rateInput = it },
                    label = { Text("倍率（如 1.0、1.2）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsed = rateInput.toDoubleOrNull()
                        if (parsed != null) {
                            viewModel.setRate(parsed)
                            showRateDialog = false
                        } else {
                            ToastManager.show("请输入有效数字")
                        }
                    }
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showRateDialog = false }) { Text("取消") }
            }
        )
    }

    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = { Text("成绩与绩点计算说明") },
            text = {
                Text(
                    "• 加权平均绩点 = Σ(课程学分 × 课程绩点 × 倍率) ÷ Σ(已勾选课程学分)\n" +
                        "• 加权平均成绩 = Σ(课程学分 × 百分制成绩 × 倍率) ÷ Σ(已勾选课程学分)\n" +
                        "• 非数字成绩（如「优秀」「良好」「合格」）将自动依据右上角「等级分设置」的规则换算，若包含未配置的文字等级请前往设置页面补充。\n" +
                        "• 可点击单门课程右侧复选框自由排除公选课或特定科目以估算推免/保研绩点。"
                )
            },
            confirmButton = {
                Button(onClick = { showHelpDialog = false }) { Text("知道了") }
            }
        )
    }
}

@Composable
private fun ScoreRowCard(
    item: CumtScoreItem,
    mappings: List<CumtScoreMappingEntry>,
    onToggle: () -> Unit
) {
    val resolved = CumtParsers.resolveEffectiveScoreAndGpa(item, mappings)
    val isUnmapped = resolved == null

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        colors = CardDefaults.cardColors(
            containerColor = if (item.isIncluded) {
                MaterialTheme.colorScheme.surfaceContainerLow
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = item.isIncluded,
                onCheckedChange = { onToggle() }
            )
            Spacer(Modifier.width(6.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.courseName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "学分 ${item.credit}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "绩点 ${item.gpa ?: resolved?.second?.let(::formatTwoDecimals) ?: "--"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (item.examType != "正常考试" && item.examType.isNotBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = MaterialTheme.shapes.extraSmall
                        ) {
                            Text(
                                text = item.examType,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                if (isUnmapped) {
                    Text(
                        text = "未映射等级「${item.scoreRaw}」，请在等级分设置中添加",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = item.scoreRaw,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (resolved != null && resolved.first < 60.0) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CumtScoreMappingScreen(
    onBack: () -> Unit,
    viewModel: CumtScoreViewModel = koinViewModel()
) {
    val mappings by viewModel.mappings.collectAsState()
    var editingEntry by remember { mutableStateOf<CumtScoreMappingEntry?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("等级分换算设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(vectorResource(Res.drawable.arrow_back_24px), contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::resetMappings) {
                        Icon(vectorResource(Res.drawable.refresh_24px), contentDescription = "恢复默认")
                    }
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(vectorResource(Res.drawable.add_24px), contentDescription = "添加映射")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    text = "点击任意条目可修改折算分数与绩点，或点击右上角新增/恢复矿大默认换算表。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            items(mappings, key = { it.rawName }) { entry ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { editingEntry = entry }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(entry.rawName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                text = "对应分数 ${entry.numericScore} · 绩点 ${entry.gpa}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { viewModel.deleteMapping(entry.rawName) }) {
                            Icon(vectorResource(Res.drawable.delete_24px), contentDescription = "删除")
                        }
                    }
                }
            }
        }
    }

    val dialogTarget = editingEntry ?: if (showAddDialog) CumtScoreMappingEntry("", 80.0, 3.0) else null
    if (dialogTarget != null) {
        var nameText by remember(dialogTarget) { mutableStateOf(dialogTarget.rawName) }
        var scoreText by remember(dialogTarget) { mutableStateOf(dialogTarget.numericScore.toString()) }
        var gpaText by remember(dialogTarget) { mutableStateOf(dialogTarget.gpa.toString()) }

        AlertDialog(
            onDismissRequest = {
                editingEntry = null
                showAddDialog = false
            },
            title = { Text(if (editingEntry != null) "编辑换算规则" else "新增换算规则") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = nameText,
                        onValueChange = { nameText = it },
                        label = { Text("等级名称（如 优秀）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = scoreText,
                        onValueChange = { scoreText = it },
                        label = { Text("对应百分制成绩") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = gpaText,
                        onValueChange = { gpaText = it },
                        label = { Text("对应绩点") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val s = scoreText.toDoubleOrNull()
                        val g = gpaText.toDoubleOrNull()
                        if (nameText.isBlank() || s == null || g == null) {
                            ToastManager.show("请填写完整且有效的数值")
                        } else {
                            viewModel.saveMapping(CumtScoreMappingEntry(nameText, s, g))
                            editingEntry = null
                            showAddDialog = false
                        }
                    }
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        editingEntry = null
                        showAddDialog = false
                    }
                ) { Text("取消") }
            }
        )
    }
}
