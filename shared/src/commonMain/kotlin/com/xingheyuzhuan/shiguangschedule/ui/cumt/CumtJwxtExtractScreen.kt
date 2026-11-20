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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xingheyuzhuan.shiguangschedule.CumtExtractMode
import com.xingheyuzhuan.shiguangschedule.cumt.CumtConstants
import com.xingheyuzhuan.shiguangschedule.cumt.CumtExamItem
import com.xingheyuzhuan.shiguangschedule.cumt.CumtExamScheduleSynchronizer
import com.xingheyuzhuan.shiguangschedule.cumt.CumtLocalRepository
import com.xingheyuzhuan.shiguangschedule.cumt.CumtParsers
import com.xingheyuzhuan.shiguangschedule.cumt.CumtScoreItem
import com.xingheyuzhuan.shiguangschedule.cumt.CumtWebScripts
import com.xingheyuzhuan.shiguangschedule.cumt.JwxtTermOptionsState
import com.xingheyuzhuan.shiguangschedule.data.repository.CourseConversionRepository
import com.xingheyuzhuan.shiguangschedule.ui.components.ToastManager
import com.xingheyuzhuan.shiguangschedule.ui.schoolselection.web.PlatformBackHandler
import com.xingheyuzhuan.shiguangschedule.ui.schoolselection.web.PlatformWebView
import com.xingheyuzhuan.shiguangschedule.ui.schoolselection.web.SslErrorData
import com.xingheyuzhuan.shiguangschedule.ui.schoolselection.web.WebBridgeHandler
import com.xingheyuzhuan.shiguangschedule.ui.schoolselection.web.WebDialogHost
import com.xingheyuzhuan.shiguangschedule.ui.schoolselection.web.WebUiEvent
import com.xingheyuzhuan.shiguangschedule.ui.schoolselection.web.WebViewController
import com.xingheyuzhuan.shiguangschedule.ui.schoolselection.web.rememberWebViewController
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import org.jetbrains.compose.resources.vectorResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinViewModel
import shiguangschedule.shared.generated.resources.Res
import shiguangschedule.shared.generated.resources.arrow_back_24px
import shiguangschedule.shared.generated.resources.arrow_drop_down_24px
import shiguangschedule.shared.generated.resources.close_24px
import shiguangschedule.shared.generated.resources.delete_24px
import shiguangschedule.shared.generated.resources.refresh_24px

@KoinViewModel
class CumtJwxtExtractViewModel(
    private val localRepo: CumtLocalRepository,
    val courseConversionRepository: CourseConversionRepository,
    private val examScheduleSynchronizer: CumtExamScheduleSynchronizer
) : ViewModel() {

    suspend fun getAutoFillJs(url: String): String? {
        val creds = localRepo.getSavedCredentials() ?: return null
        return CumtWebScripts.autoFillAndNavigateJs(url, creds.first, creds.second)
    }

    fun saveImportedExams(items: List<CumtExamItem>, onPromptSync: () -> Unit) {
        viewModelScope.launch {
            localRepo.addOrMergeExams(items, keepCustom = true)
            ToastManager.show("已保存 ${items.size} 条考试安排")
            onPromptSync()
        }
    }

    fun syncUpcomingExamsAfterImport(onDone: () -> Unit) {
        viewModelScope.launch {
            val report = examScheduleSynchronizer.syncAllUpcomingToCurrentTable()
            if (report.errorMessage != null) {
                ToastManager.show(report.errorMessage)
            } else {
                ToastManager.show("已将 ${report.syncedCount} 场待考同步进当前课表")
            }
            onDone()
        }
    }

    fun saveImportedScores(items: List<CumtScoreItem>, onDone: () -> Unit) {
        viewModelScope.launch {
            localRepo.saveScores(items)
            ToastManager.show("已导入 ${items.size} 门课程成绩")
            onDone()
        }
    }
}

private suspend fun evaluateJsSuspend(
    controller: WebViewController,
    script: String
): String? = suspendCancellableCoroutine { cont ->
    var resumed = false
    controller.evaluateJavascript(script) { raw ->
        if (!resumed && cont.isActive) {
            resumed = true
            cont.resume(raw)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CumtJwxtExtractScreen(
    mode: CumtExtractMode,
    onBack: () -> Unit,
    viewModel: CumtJwxtExtractViewModel = koinViewModel()
) {
    val controller = rememberWebViewController()
    val coroutineScope = rememberCoroutineScope()
    val uiEventChannel = remember { Channel<WebUiEvent>(Channel.UNLIMITED) }
    val uiEventsFlow = remember(uiEventChannel) { uiEventChannel.receiveAsFlow() }
    val bridgeHandler = remember(coroutineScope, viewModel.courseConversionRepository, controller) {
        WebBridgeHandler(
            coroutineScope = coroutineScope,
            uiEventChannel = uiEventChannel,
            courseConversionRepository = viewModel.courseConversionRepository,
            onTaskCompleted = {},
            evaluateJs = { script, callback -> controller.evaluateJavascript(script, callback) }
        )
    }

    val initialUrl = remember(mode) {
        when (mode) {
            CumtExtractMode.EXAM -> CumtConstants.JWXT_EXAM_URL
            CumtExtractMode.SCORE -> CumtConstants.JWXT_SCORE_URL
        }
    }
    val targetMenuCode = remember(mode) {
        if (mode == CumtExtractMode.EXAM) CumtConstants.JWXT_MENU_EXAM else CumtConstants.JWXT_MENU_SCORE
    }
    val targetRelativePath = remember(mode) {
        if (mode == CumtExtractMode.EXAM) {
            "/jwglxt/kwgl/kscx_cxXsksxxIndex.html?gnmkdm=${CumtConstants.JWXT_MENU_EXAM}&layout=default"
        } else {
            "/jwglxt/cjcx/cjcx_cxDgXscj.html?gnmkdm=${CumtConstants.JWXT_MENU_SCORE}&layout=default"
        }
    }
    val targetPathKey = remember(mode) {
        if (mode == CumtExtractMode.EXAM) "kscx_cxXsksxxIndex" else "cjcx_cxDgXscj"
    }

    var pageTitle by remember {
        mutableStateOf(if (mode == CumtExtractMode.EXAM) "教务考试导入" else "教务成绩导入")
    }
    var progress by remember { mutableFloatStateOf(0f) }

    val stagedExams = remember { mutableStateListOf<CumtExamItem>() }
    val stagedScores = remember { mutableStateListOf<CumtScoreItem>() }
    var showStagedSheet by remember { mutableStateOf(false) }
    var showSyncConfirmDialog by remember { mutableStateOf(false) }

    var termState by remember { mutableStateOf(JwxtTermOptionsState()) }
    var selectedYear by remember { mutableStateOf("") }
    var selectedTerm by remember { mutableStateOf("") }
    var showYearMenu by remember { mutableStateOf(false) }
    var showTermMenu by remember { mutableStateOf(false) }
    var isAutoExtracting by remember { mutableStateOf(false) }
    var autoStatusMessage by remember { mutableStateOf<String?>(null) }
    var autoQueriedUrl by remember { mutableStateOf("") }

    val stagedCount = if (mode == CumtExtractMode.EXAM) stagedExams.size else stagedScores.size

    fun stageHtmlFromCurrentPage(html: String): Pair<Int, Int> {
        return if (mode == CumtExtractMode.EXAM) {
            val parsed = CumtParsers.parseExamHtml(html)
            val existingIds = stagedExams.mapTo(HashSet()) { it.id }
            val added = parsed.filter { it.id !in existingIds }
            stagedExams.addAll(added)
            parsed.size to added.size
        } else {
            val parsed = CumtParsers.parseScoreHtml(html)
            val existingIds = stagedScores.mapTo(HashSet()) { it.id }
            val added = parsed.filter { it.id !in existingIds }
            stagedScores.addAll(added)
            parsed.size to added.size
        }
    }

    fun captureCurrentPageOnce() {
        if (isAutoExtracting) return
        controller.evaluateJavascript(CumtWebScripts.extractJwxtGridHtmlJs()) { raw ->
            val html = CumtWebScripts.decodeEvalResult(raw).orEmpty()
            if (html.isBlank()) {
                ToastManager.show("未读取到网页内容")
                return@evaluateJavascript
            }
            val (parsedCount, addedCount) = stageHtmlFromCurrentPage(html)
            val unitLabel = if (mode == CumtExtractMode.EXAM) "条考试" else "门成绩"
            ToastManager.show(
                if (parsedCount == 0) "当前页未识别到有效记录"
                else "本页新增 $addedCount $unitLabel，共暂存 $stagedCount 项"
            )
        }
    }

    suspend fun refreshTermSelectors() {
        val raw = evaluateJsSuspend(controller, CumtWebScripts.inspectJwxtPageStateJs())
        val state = CumtWebScripts.parseJwxtTermOptionsState(raw)
        if (state.hasSelectors) {
            termState = state
            selectedYear = state.selectedYear
            selectedTerm = state.selectedTerm
        }
    }

    fun startAutoPaginatedExtract() {
        if (isAutoExtracting) return
        coroutineScope.launch {
            isAutoExtracting = true
            try {
                autoStatusMessage = "正在检查查询状态..."
                var state = CumtWebScripts.parseJwxtTermOptionsState(
                    evaluateJsSuspend(controller, CumtWebScripts.inspectJwxtPageStateJs())
                )
                if (state.hasSelectors) {
                    termState = state
                }
                if (state.rowCount == 0 && state.hasSelectors) {
                    autoStatusMessage = "正在执行查询..."
                    evaluateJsSuspend(
                        controller,
                        CumtWebScripts.selectJwxtTermAndQueryJs(selectedYear, selectedTerm, triggerSearch = true)
                    )
                    delay(350)
                    for (attempt in 0 until 40) {
                        val polled = CumtWebScripts.parseJwxtTermOptionsState(
                            evaluateJsSuspend(controller, CumtWebScripts.inspectJwxtPageStateJs())
                        )
                        state = polled
                        if (!polled.isLoading && (polled.rowCount > 0 || attempt >= 12)) {
                            break
                        }
                        delay(250)
                    }
                }
                if (state.rowCount == 0 && state.totalPages <= 0) {
                    ToastManager.show("当前查询条件暂无数据，可调整上方学年/学期后再试")
                    return@launch
                }

                if (state.currentPage > 1) {
                    autoStatusMessage = "正在回到第 1 页..."
                    val firstRes = CumtWebScripts.decodeEvalResult(
                        evaluateJsSuspend(controller, CumtWebScripts.jwxtPagerActionJs("first"))
                    )
                    if (firstRes == "clicked") {
                        delay(250)
                        repeat(40) {
                            val polled = CumtWebScripts.parseJwxtTermOptionsState(
                                evaluateJsSuspend(controller, CumtWebScripts.inspectJwxtPageStateJs())
                            )
                            state = polled
                            if (!polled.isLoading && polled.currentPage == 1) {
                                return@repeat
                            }
                            delay(250)
                        }
                    }
                }

                var totalAdded = 0
                var fetchedPages = 0
                for (pageIndex in 0 until 100) {
                    val pageHint = if (state.totalPages > 0) "${state.currentPage}/${state.totalPages}" else "${state.currentPage}"
                    autoStatusMessage = "正在抓取第 $pageHint 页..."

                    val gridHtml = CumtWebScripts.decodeEvalResult(
                        evaluateJsSuspend(controller, CumtWebScripts.extractJwxtGridHtmlJs())
                    ).orEmpty()
                    val lastHtmlHash = gridHtml.hashCode()
                    if (gridHtml.isNotBlank()) {
                        val (_, addedCount) = stageHtmlFromCurrentPage(gridHtml)
                        totalAdded += addedCount
                    }
                    fetchedPages++

                    if (!state.hasNextPage) break
                    val prevPage = state.currentPage
                    val nextClickRes = CumtWebScripts.decodeEvalResult(
                        evaluateJsSuspend(controller, CumtWebScripts.jwxtPagerActionJs("next"))
                    )
                    if (nextClickRes != "clicked") break

                    delay(250)
                    var advanced = false
                    for (attempt in 0 until 60) {
                        val nextState = CumtWebScripts.parseJwxtTermOptionsState(
                            evaluateJsSuspend(controller, CumtWebScripts.inspectJwxtPageStateJs())
                        )
                        if (!nextState.isLoading) {
                            val newGridHash = CumtWebScripts.decodeEvalResult(
                                evaluateJsSuspend(controller, CumtWebScripts.extractJwxtGridHtmlJs())
                            ).orEmpty().hashCode()
                            if (nextState.currentPage != prevPage || newGridHash != lastHtmlHash) {
                                state = nextState
                                advanced = true
                                break
                            }
                        }
                        delay(250)
                    }
                    if (!advanced) break
                }

                val unitLabel = if (mode == CumtExtractMode.EXAM) "条考试" else "门成绩"
                ToastManager.show(
                    if (totalAdded > 0) {
                        "已自动扫描 $fetchedPages 页，新增 $totalAdded $unitLabel（共暂存 $stagedCount 项）"
                    } else if (stagedCount > 0) {
                        "已扫描 $fetchedPages 页，共暂存 $stagedCount 项（无新增重复项）"
                    } else {
                        "当前查询结果为空，未提取到$unitLabel"
                    }
                )
            } finally {
                isAutoExtracting = false
                autoStatusMessage = null
            }
        }
    }

    PlatformBackHandler(enabled = true) {
        if (controller.canGoBack()) {
            controller.goBack()
        } else {
            onBack()
        }
    }

    Scaffold(
        topBar = {
            CumtTopBar(
                title = pageTitle,
                subtitle = if (termState.hasSelectors) {
                    "已识别学年学期 · 可直接点下方「自动抓全页」"
                } else {
                    "登录后将自动定位页面并展开原生物理学期选项"
                },
                onBack = onBack,
                navIcon = vectorResource(Res.drawable.close_24px),
                actions = {
                    IconButton(onClick = { controller.reload() }) {
                        Icon(vectorResource(Res.drawable.refresh_24px), contentDescription = "刷新")
                    }
                }
            )
        },
        bottomBar = {
            BottomAppBar(
                modifier = Modifier.navigationBarsPadding(),
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = { if (controller.canGoBack()) controller.goBack() }
                    ) {
                        Icon(vectorResource(Res.drawable.arrow_back_24px), contentDescription = "后退")
                    }

                    OutlinedButton(
                        onClick = { showStagedSheet = true },
                        shape = RoundedCornerShape(50),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        modifier = Modifier.weight(0.95f)
                    ) {
                        Text(
                            text = "已暂存 ($stagedCount)",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    FilledTonalButton(
                        enabled = !isAutoExtracting,
                        shape = RoundedCornerShape(50),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier.weight(1.1f),
                        onClick = ::startAutoPaginatedExtract
                    ) {
                        if (isAutoExtracting) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(6.dp))
                            Text("抓取中", maxLines = 1)
                        } else {
                            Text("自动抓全页", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }

                    Button(
                        enabled = stagedCount > 0 && !isAutoExtracting,
                        shape = RoundedCornerShape(50),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        onClick = {
                            if (mode == CumtExtractMode.EXAM) {
                                viewModel.saveImportedExams(stagedExams.toList()) {
                                    showSyncConfirmDialog = true
                                }
                            } else {
                                viewModel.saveImportedScores(stagedScores.toList(), onBack)
                            }
                        }
                    ) {
                        Text("导入")
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            Surface(
                color = CumtAccent.OCEAN.containerColor().copy(alpha = 0.65f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = autoStatusMessage ?: if (mode == CumtExtractMode.EXAM) {
                        "提示：考表支持自动翻页全量抓取（含座位号与起止时间），导入后可同步到课表。"
                    } else {
                        "提示：选择「全部」学年可直接抓全量成绩；点「自动抓全页」将自动翻页汇总。"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = CumtAccent.OCEAN.contentColor(),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }

            if (termState.hasSelectors) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val yearLabel = termState.years.firstOrNull { it.value == selectedYear }?.label
                            ?: if (selectedYear.isEmpty()) "全部学年" else selectedYear
                        val termLabel = termState.terms.firstOrNull { it.value == selectedTerm }?.label
                            ?: if (selectedTerm.isEmpty()) "全部学期" else selectedTerm

                        Box(modifier = Modifier.weight(1.1f)) {
                            OutlinedButton(
                                onClick = { showYearMenu = true },
                                enabled = !isAutoExtracting && termState.years.isNotEmpty(),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = yearLabel,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    vectorResource(Res.drawable.arrow_drop_down_24px),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = showYearMenu,
                                onDismissRequest = { showYearMenu = false }
                            ) {
                                termState.years.forEach { opt ->
                                    DropdownMenuItem(
                                        text = { Text(opt.label.ifBlank { "全部" }) },
                                        onClick = {
                                            selectedYear = opt.value
                                            showYearMenu = false
                                            controller.executeScript(
                                                CumtWebScripts.selectJwxtTermAndQueryJs(opt.value, selectedTerm, triggerSearch = false)
                                            )
                                        }
                                    )
                                }
                            }
                        }

                        Box(modifier = Modifier.weight(0.9f)) {
                            OutlinedButton(
                                onClick = { showTermMenu = true },
                                enabled = !isAutoExtracting && termState.terms.isNotEmpty(),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = termLabel,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    vectorResource(Res.drawable.arrow_drop_down_24px),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = showTermMenu,
                                onDismissRequest = { showTermMenu = false }
                            ) {
                                termState.terms.forEach { opt ->
                                    DropdownMenuItem(
                                        text = { Text(opt.label.ifBlank { "全部" }) },
                                        onClick = {
                                            selectedTerm = opt.value
                                            showTermMenu = false
                                            controller.executeScript(
                                                CumtWebScripts.selectJwxtTermAndQueryJs(selectedYear, opt.value, triggerSearch = false)
                                            )
                                        }
                                    )
                                }
                            }
                        }

                        FilledTonalButton(
                            onClick = {
                                coroutineScope.launch {
                                    controller.executeScript(
                                        CumtWebScripts.selectJwxtTermAndQueryJs(selectedYear, selectedTerm, triggerSearch = true)
                                    )
                                    delay(900)
                                    refreshTermSelectors()
                                }
                            },
                            enabled = !isAutoExtracting,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("查询", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                PlatformWebView(
                    modifier = Modifier.fillMaxSize(),
                    url = initialUrl,
                    isDesktopMode = true,
                    isDevToolsEnabled = false,
                    controller = controller,
                    bridgeHandler = bridgeHandler,
                    onProgressChange = { progress = it },
                    onTitleChange = { if (it.isNotBlank()) pageTitle = it },
                    onNavigateToSchedule = {},
                    onPageLoaded = { loadedUrl ->
                        CumtWebScripts.mobileAdaptationJs(loadedUrl)?.let { controller.executeScript(it) }
                        coroutineScope.launch {
                            viewModel.getAutoFillJs(loadedUrl)?.let { controller.executeScript(it) }
                            if (loadedUrl.contains("jwxt.cumt.edu.cn/jwglxt/xtgl/index", ignoreCase = true)) {
                                controller.executeScript(CumtWebScripts.jwxtAutoNavigateJs(targetMenuCode, targetRelativePath))
                                return@launch
                            }
                            if (!loadedUrl.contains(targetPathKey, ignoreCase = true)) {
                                termState = JwxtTermOptionsState()
                                return@launch
                            }

                            delay(450)
                            var inspected = CumtWebScripts.parseJwxtTermOptionsState(
                                evaluateJsSuspend(controller, CumtWebScripts.inspectJwxtPageStateJs())
                            )
                            if (!inspected.hasSelectors) {
                                delay(700)
                                inspected = CumtWebScripts.parseJwxtTermOptionsState(
                                    evaluateJsSuspend(controller, CumtWebScripts.inspectJwxtPageStateJs())
                                )
                            }
                            if (inspected.hasSelectors) {
                                termState = inspected
                                selectedYear = inspected.selectedYear
                                selectedTerm = inspected.selectedTerm
                                if (autoQueriedUrl != loadedUrl) {
                                    autoQueriedUrl = loadedUrl
                                    if (mode == CumtExtractMode.SCORE) {
                                        val hasAllYear = inspected.years.any { it.value.isEmpty() }
                                        val targetY = if (hasAllYear) "" else inspected.selectedYear
                                        val targetT = if (hasAllYear) "" else inspected.selectedTerm
                                        selectedYear = targetY
                                        selectedTerm = targetT
                                        evaluateJsSuspend(
                                            controller,
                                            CumtWebScripts.selectJwxtTermAndQueryJs(targetY, targetT, triggerSearch = true)
                                        )
                                        delay(900)
                                        refreshTermSelectors()
                                    } else {
                                        val targetY = inspected.selectedYear.ifEmpty {
                                            inspected.years.firstOrNull { it.value.isNotBlank() }?.value.orEmpty()
                                        }
                                        val targetT = inspected.selectedTerm
                                        selectedYear = targetY
                                        selectedTerm = targetT
                                        if (inspected.rowCount == 0) {
                                            evaluateJsSuspend(
                                                controller,
                                                CumtWebScripts.selectJwxtTermAndQueryJs(targetY, targetT, triggerSearch = true)
                                            )
                                            delay(900)
                                            refreshTermSelectors()
                                        }
                                    }
                                }
                            }
                        }
                    },
                    onSslError = { failingUrl, onProceed, onCancel ->
                        uiEventChannel.trySend(
                            WebUiEvent.ShowSslWarning(
                                SslErrorData(
                                    primaryErrorUrl = failingUrl,
                                    onProceed = onProceed,
                                    onCancel = onCancel
                                )
                            )
                        )
                    }
                )
                if (progress < 1.0f || isAutoExtracting) {
                    LinearProgressIndicator(
                        progress = { if (isAutoExtracting) 0.5f else progress },
                        modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                        trackColor = Color.Transparent
                    )
                }
                WebDialogHost(uiEvents = uiEventsFlow)
            }
        }
    }

    if (showStagedSheet) {
        ModalBottomSheet(onDismissRequest = { showStagedSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "已暂存记录 ($stagedCount)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                showStagedSheet = false
                                captureCurrentPageOnce()
                            },
                            enabled = !isAutoExtracting,
                            shape = RoundedCornerShape(50)
                        ) { Text("仅抓本页") }
                        if (stagedCount > 0) {
                            OutlinedButton(
                                onClick = {
                                    stagedExams.clear()
                                    stagedScores.clear()
                                },
                                shape = RoundedCornerShape(50)
                            ) { Text("清空") }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (stagedCount == 0) {
                    Text(
                        text = "暂无暂存数据。可点击底部「自动抓全页」，或先点「查询」再点击上方「仅抓本页」。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.height(320.dp)
                    ) {
                        if (mode == CumtExtractMode.EXAM) {
                            items(stagedExams, key = { it.id }) { exam ->
                                CumtSurfaceCard(
                                    cornerRadius = 14.dp,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(exam.courseName, fontWeight = FontWeight.SemiBold)
                                            val seatStr = exam.seatNumber.takeIf { it.isNotBlank() }?.let { " · 座$it" }.orEmpty()
                                            Text(
                                                "${exam.dateTimeRaw} ${exam.location}$seatStr".trim(),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        IconButton(onClick = { stagedExams.remove(exam) }) {
                                            Icon(vectorResource(Res.drawable.delete_24px), contentDescription = "移除")
                                        }
                                    }
                                }
                            }
                        } else {
                            items(stagedScores, key = { it.id }) { score ->
                                CumtSurfaceCard(
                                    cornerRadius = 14.dp,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(score.courseName, fontWeight = FontWeight.SemiBold)
                                            Text(
                                                "成绩 ${score.scoreRaw} · 学分 ${score.credit} · 绩点 ${score.gpa ?: "--"}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        IconButton(onClick = { stagedScores.remove(score) }) {
                                            Icon(vectorResource(Res.drawable.delete_24px), contentDescription = "移除")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    if (showSyncConfirmDialog) {
        AlertDialog(
            onDismissRequest = {
                showSyncConfirmDialog = false
                onBack()
            },
            title = { Text("考试已保存，是否插入课表？") },
            text = { Text("立即将本次待考安排写入当前课表，即可在周课表、今日课表、桌面小组件查看，并享受考前提醒与自动免打扰。") },
            confirmButton = {
                Button(
                    onClick = {
                        showSyncConfirmDialog = false
                        viewModel.syncUpcomingExamsAfterImport(onBack)
                    }
                ) { Text("立即同步到课表") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showSyncConfirmDialog = false
                        onBack()
                    }
                ) { Text("稍后手动同步") }
            }
        )
    }
}
