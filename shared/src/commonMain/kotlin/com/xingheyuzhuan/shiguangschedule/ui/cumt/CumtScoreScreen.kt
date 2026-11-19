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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
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
import shiguangschedule.shared.generated.resources.award_star_24px
import shiguangschedule.shared.generated.resources.close_24px
import shiguangschedule.shared.generated.resources.delete_24px
import shiguangschedule.shared.generated.resources.download_24px
import shiguangschedule.shared.generated.resources.info_24px
import shiguangschedule.shared.generated.resources.refresh_24px
import shiguangschedule.shared.generated.resources.school_24px
import shiguangschedule.shared.generated.resources.search_24px
import shiguangschedule.shared.generated.resources.tune_24px
import kotlin.math.abs
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
    val selectedRateSegment = when {
        abs(rate - 1.0) < 0.001 -> 0
        abs(rate - 1.2) < 0.001 -> 1
        else -> 2
    }

    Scaffold(
        topBar = {
            CumtTopBar(
                title = "成绩与绩点",
                subtitle = if (scores.isEmpty()) "支持 GPA 倍率与等级分定制" else "共 ${scores.size} 门课程 · 已选 ${summary.includedCount} 门",
                onBack = onBack,
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
            // 顶部 GPA 仪表盘卡片
            CumtHeroGradientCard(
                gradientColors = listOf(Color(0xFF2843A8), Color(0xFF0E7C86)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column {
                            Text(
                                text = "加权平均绩点 (GPA)",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.82f)
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = formatTwoDecimals(summary.weightedGpa),
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "加权平均分",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.82f)
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = formatTwoDecimals(summary.weightedScore),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(
                                color = Color.White.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(50)
                            ) {
                                Text(
                                    text = "${summary.includedCount} 门 · ${formatTwoDecimals(summary.totalCredits)} 学分",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                            Surface(
                                color = Color.White.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(50),
                                onClick = { showRateDialog = true }
                            ) {
                                Text(
                                    text = "${formatTwoDecimals(rate)}x 倍率",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                        Surface(
                            color = Color.White.copy(alpha = 0.22f),
                            shape = RoundedCornerShape(50),
                            onClick = {
                                onNavigate(Destination.WebView(CumtConstants.JWXT_SCORE_DETAIL_URL, null))
                            }
                        ) {
                            Text(
                                text = "分项明细 ›",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }

            // 倍率切换分段栏
            CumtSegmentedBar(
                options = listOf(
                    "1.0x 标准",
                    "1.2x 推免",
                    if (selectedRateSegment == 2) "自定义(${formatTwoDecimals(rate)}x)" else "自定义"
                ),
                selectedIndex = selectedRateSegment,
                onSelect = { idx ->
                    when (idx) {
                        0 -> viewModel.setRate(1.0)
                        1 -> viewModel.setRate(1.2)
                        else -> showRateDialog = true
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            )

            // 搜索与排序筛选栏
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
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(vectorResource(Res.drawable.close_24px), contentDescription = "清空")
                            }
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )

                Box {
                    OutlinedButton(
                        onClick = { showSortMenu = true },
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(if (sortOption == ScoreSortOption.DEFAULT) "排序" else sortOption.displayName)
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
                    FilledTonalButton(
                        onClick = { viewModel.selectAll(!allIncluded) },
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(if (allIncluded) "全不选" else "全选")
                    }
                }
            }

            if (displayedScores.isEmpty()) {
                CumtEmptyState(
                    icon = Res.drawable.award_star_24px,
                    accent = CumtAccent.INDIGO,
                    title = if (scores.isEmpty()) "暂未导入课程成绩" else "没有匹配的成绩记录",
                    subtitle = if (scores.isEmpty()) {
                        "登录正方教务系统后即可一键导入全部课程成绩与学分绩点"
                    } else {
                        "尝试更换搜索关键词，或清空当前筛选条件"
                    },
                    primaryActionText = if (scores.isEmpty()) "前往正方教务抓取成绩" else "清空搜索条件",
                    onPrimaryAction = {
                        if (scores.isEmpty()) {
                            onNavigate(Destination.CumtJwxtExtract(CumtExtractMode.SCORE))
                        } else {
                            searchQuery = ""
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
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
                    shape = RoundedCornerShape(14.dp),
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
                        "• 可点击单门课程自由勾选或排除公选课与特定科目，实时估算推免/保研绩点。"
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
    val scoreAccent = when {
        resolved == null -> CumtAccent.SLATE
        resolved.first >= 90.0 -> CumtAccent.EMERALD
        resolved.first >= 80.0 -> CumtAccent.INDIGO
        resolved.first >= 70.0 -> CumtAccent.SKY
        resolved.first >= 60.0 -> CumtAccent.AMBER
        else -> CumtAccent.CORAL
    }

    CumtSurfaceCard(
        onClick = onToggle,
        containerColor = if (item.isIncluded) {
            MaterialTheme.colorScheme.surfaceContainerLow
        } else {
            MaterialTheme.colorScheme.surfaceContainerLowest
        },
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (item.isIncluded) 1f else 0.62f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
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
                Spacer(Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CumtStatusPill(
                        text = "学分 ${item.credit}",
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    CumtStatusPill(
                        text = "绩点 ${item.gpa ?: resolved?.second?.let(::formatTwoDecimals) ?: "--"}",
                        containerColor = CumtAccent.TEAL.containerColor(),
                        contentColor = CumtAccent.TEAL.contentColor()
                    )
                    if (item.examType != "正常考试" && item.examType.isNotBlank()) {
                        CumtStatusPill(
                            text = item.examType,
                            containerColor = CumtAccent.AMBER.containerColor(),
                            contentColor = CumtAccent.AMBER.contentColor()
                        )
                    }
                }
                if (isUnmapped) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "未映射等级「${item.scoreRaw}」，请在等级分设置中添加",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Surface(
                color = scoreAccent.containerColor(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = item.scoreRaw,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = scoreAccent.contentColor()
                    )
                }
            }
        }
    }
}

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
            CumtTopBar(
                title = "等级分换算设置",
                subtitle = "点击规则条目可直接编辑",
                onBack = onBack,
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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                CumtSurfaceCard(
                    containerColor = CumtAccent.INDIGO.containerColor().copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CumtIconBadge(
                            icon = Res.drawable.tune_24px,
                            accent = CumtAccent.INDIGO,
                            size = 40.dp,
                            iconSize = 20.dp
                        )
                        Text(
                            text = "文字成绩将依据此表折算为百分制与绩点。可点击条目调整或右上角恢复矿大默认表。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
            items(mappings, key = { it.rawName }) { entry ->
                CumtSurfaceCard(
                    onClick = { editingEntry = entry },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = entry.rawName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                CumtStatusPill(
                                    text = "百分制 ${entry.numericScore}",
                                    containerColor = CumtAccent.SKY.containerColor(),
                                    contentColor = CumtAccent.SKY.contentColor()
                                )
                                CumtStatusPill(
                                    text = "绩点 ${entry.gpa}",
                                    containerColor = CumtAccent.TEAL.containerColor(),
                                    contentColor = CumtAccent.TEAL.contentColor()
                                )
                            }
                        }
                        IconButton(onClick = { viewModel.deleteMapping(entry.rawName) }) {
                            Icon(
                                vectorResource(Res.drawable.delete_24px),
                                contentDescription = "删除",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
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
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = nameText,
                        onValueChange = { nameText = it },
                        label = { Text("等级名称（如 优秀）") },
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = scoreText,
                        onValueChange = { scoreText = it },
                        label = { Text("对应百分制成绩") },
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = gpaText,
                        onValueChange = { gpaText = it },
                        label = { Text("对应绩点") },
                        shape = RoundedCornerShape(14.dp),
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
