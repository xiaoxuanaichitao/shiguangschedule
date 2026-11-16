package com.xingheyuzhuan.shiguangschedule.ui.cumt

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
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
import com.xingheyuzhuan.shiguangschedule.cumt.CumtLocalRepository
import com.xingheyuzhuan.shiguangschedule.cumt.CumtParsers
import com.xingheyuzhuan.shiguangschedule.cumt.CumtScoreItem
import com.xingheyuzhuan.shiguangschedule.cumt.CumtWebScripts
import com.xingheyuzhuan.shiguangschedule.data.repository.CourseConversionRepository
import com.xingheyuzhuan.shiguangschedule.ui.components.ToastManager
import com.xingheyuzhuan.shiguangschedule.ui.schoolselection.web.PlatformBackHandler
import com.xingheyuzhuan.shiguangschedule.ui.schoolselection.web.PlatformWebView
import com.xingheyuzhuan.shiguangschedule.ui.schoolselection.web.SslErrorData
import com.xingheyuzhuan.shiguangschedule.ui.schoolselection.web.WebBridgeHandler
import com.xingheyuzhuan.shiguangschedule.ui.schoolselection.web.WebDialogHost
import com.xingheyuzhuan.shiguangschedule.ui.schoolselection.web.WebUiEvent
import com.xingheyuzhuan.shiguangschedule.ui.schoolselection.web.rememberWebViewController
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.vectorResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinViewModel
import shiguangschedule.shared.generated.resources.Res
import shiguangschedule.shared.generated.resources.arrow_back_24px
import shiguangschedule.shared.generated.resources.close_24px
import shiguangschedule.shared.generated.resources.delete_24px
import shiguangschedule.shared.generated.resources.refresh_24px

@KoinViewModel
class CumtJwxtExtractViewModel(
    private val localRepo: CumtLocalRepository,
    val courseConversionRepository: CourseConversionRepository
) : ViewModel() {

    suspend fun getAutoFillJs(url: String): String? {
        val creds = localRepo.getSavedCredentials() ?: return null
        return CumtWebScripts.autoFillAndNavigateJs(url, creds.first, creds.second)
    }

    fun saveImportedExams(items: List<CumtExamItem>, onDone: () -> Unit) {
        viewModelScope.launch {
            localRepo.addOrMergeExams(items, keepCustom = true)
            ToastManager.show("已导入 ${items.size} 条考试安排")
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
    var pageTitle by remember {
        mutableStateOf(if (mode == CumtExtractMode.EXAM) "教务考试导入" else "教务成绩导入")
    }
    var progress by remember { mutableFloatStateOf(0f) }

    val stagedExams = remember { mutableStateListOf<CumtExamItem>() }
    val stagedScores = remember { mutableStateListOf<CumtScoreItem>() }
    var showStagedSheet by remember { mutableStateOf(false) }

    val stagedCount = if (mode == CumtExtractMode.EXAM) stagedExams.size else stagedScores.size

    PlatformBackHandler(enabled = true) {
        if (controller.canGoBack()) {
            controller.goBack()
        } else {
            onBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(pageTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            text = "登录并点击「查询」加载表格后，点底部「抓取本页」（可翻页累加）",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(vectorResource(Res.drawable.close_24px), contentDescription = "关闭")
                    }
                },
                actions = {
                    IconButton(onClick = { controller.reload() }) {
                        Icon(vectorResource(Res.drawable.refresh_24px), contentDescription = "刷新")
                    }
                }
            )
        },
        bottomBar = {
            BottomAppBar(modifier = Modifier.navigationBarsPadding()) {
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
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("已暂存 ($stagedCount)")
                    }

                    OutlinedButton(
                        onClick = {
                            controller.evaluateJavascript(CumtWebScripts.extractJwxtGridHtmlJs()) { raw ->
                                val html = CumtWebScripts.decodeEvalResult(raw).orEmpty()
                                if (html.isBlank()) {
                                    ToastManager.show("未读取到网页内容")
                                    return@evaluateJavascript
                                }
                                if (mode == CumtExtractMode.EXAM) {
                                    val parsed = CumtParsers.parseExamHtml(html)
                                    val existingIds = stagedExams.map { it.id }.toSet()
                                    val added = parsed.filter { it.id !in existingIds }
                                    stagedExams.addAll(added)
                                    ToastManager.show(
                                        if (parsed.isEmpty()) "当前页未识别到考试记录"
                                        else "本页新增 ${added.size} 条，共暂存 ${stagedExams.size} 条"
                                    )
                                } else {
                                    val parsed = CumtParsers.parseScoreHtml(html)
                                    val existingIds = stagedScores.map { it.id }.toSet()
                                    val added = parsed.filter { it.id !in existingIds }
                                    stagedScores.addAll(added)
                                    ToastManager.show(
                                        if (parsed.isEmpty()) "当前页未识别到成绩记录"
                                        else "本页新增 ${added.size} 门，共暂存 ${stagedScores.size} 门"
                                    )
                                }
                            }
                        }
                    ) {
                        Text("抓取本页")
                    }

                    Button(
                        enabled = stagedCount > 0,
                        onClick = {
                            if (mode == CumtExtractMode.EXAM) {
                                viewModel.saveImportedExams(stagedExams.toList(), onBack)
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
        Box(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
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
                    coroutineScope.launch {
                        viewModel.getAutoFillJs(loadedUrl)?.let { controller.executeScript(it) }
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
            if (progress < 1.0f) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                    trackColor = Color.Transparent
                )
            }
            WebDialogHost(uiEvents = uiEventsFlow)
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
                    if (stagedCount > 0) {
                        OutlinedButton(onClick = {
                            stagedExams.clear()
                            stagedScores.clear()
                        }) { Text("清空") }
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (stagedCount == 0) {
                    Text(
                        text = "暂无暂存数据。请在教务表格加载出来后点击底部「抓取本页」。",
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
                                Card(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(exam.courseName, fontWeight = FontWeight.SemiBold)
                                            Text(
                                                "${exam.dateTimeRaw} ${exam.location}".trim(),
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
                                Card(modifier = Modifier.fillMaxWidth()) {
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
}
