package com.xingheyuzhuan.shiguangschedule.ui.cumt

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.xingheyuzhuan.shiguangschedule.Destination
import com.xingheyuzhuan.shiguangschedule.cumt.CumtAiActionStatus
import com.xingheyuzhuan.shiguangschedule.cumt.CumtAiChatMessage
import com.xingheyuzhuan.shiguangschedule.cumt.CumtAiConfig
import com.xingheyuzhuan.shiguangschedule.cumt.CumtAiPendingAction
import com.xingheyuzhuan.shiguangschedule.cumt.CumtAiSessionRepository
import com.xingheyuzhuan.shiguangschedule.cumt.CumtLocalRepository
import com.xingheyuzhuan.shiguangschedule.cumt.ShiguangMcpServerManager
import com.xingheyuzhuan.shiguangschedule.tool.FileManagerCallbacks
import com.xingheyuzhuan.shiguangschedule.tool.rememberFileManager
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.vectorResource
import org.koin.compose.koinInject
import shiguangschedule.shared.generated.resources.Res
import shiguangschedule.shared.generated.resources.arrow_forward_24px
import shiguangschedule.shared.generated.resources.check_circle_24px
import shiguangschedule.shared.generated.resources.close_24px
import shiguangschedule.shared.generated.resources.delete_24px
import shiguangschedule.shared.generated.resources.image_24px
import shiguangschedule.shared.generated.resources.tune_24px
import kotlin.math.roundToInt

/**
 * 全局 Notion 风格 AI 悬浮小球 + 半屏灵动对话抽屉。
 * 挂载在 AppNavigation 顶层，可在课表/成绩/考试等任意页面快速唤起并实时看到课表变更。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CumtAiFloatingOverlay(
    currentDestination: Destination,
    onNavigate: (Destination) -> Unit
) {
    val localRepo: CumtLocalRepository = koinInject()
    val sessionRepo: CumtAiSessionRepository = koinInject()
    val mcpManager: ShiguangMcpServerManager = koinInject()
    val scope = rememberCoroutineScope()

    val aiConfig by localRepo.aiConfigFlow.collectAsState(initial = CumtAiConfig())
    val messages by sessionRepo.messages.collectAsState()
    val isBusy by sessionRepo.isBusy.collectAsState()
    val hasUnreadReply by sessionRepo.hasUnreadReply.collectAsState()
    val isQuickSheetOpen by sessionRepo.isQuickSheetOpen.collectAsState()

    // 若用户开启了 MCP 服务，启动应用时自动拉起监听
    LaunchedEffect(Unit) {
        mcpManager.ensureAutoStartIfEnabled()
    }

    // 在 AI 全屏页、教务 WebView 登录/抓课页自动避让隐藏悬浮球
    val shouldShowOrb = aiConfig.floatingOrbEnabled &&
        currentDestination != Destination.CumtAiCourseImport &&
        currentDestination !is Destination.WebView &&
        currentDestination !is Destination.CumtJwxtExtract

    val contextHint = remember(currentDestination) {
        when (currentDestination) {
            Destination.CourseSchedule -> "用户当前正在查看周课表"
            Destination.TodaySchedule -> "用户当前正在查看今日日程"
            Destination.CumtScores -> "用户当前正在查看矿大成绩与绩点页"
            Destination.CumtExams -> "用户当前正在查看矿大考试倒计时页"
            Destination.CampusDiscover -> "用户当前在矿大校园服务主页"
            else -> "用户当前在应用内"
        }
    }

    if (shouldShowOrb) {
        AiDraggableFloatingOrb(
            savedYFraction = aiConfig.floatingOrbYFraction,
            savedOnRight = aiConfig.floatingOrbOnRight,
            isBusy = isBusy,
            hasUnread = hasUnreadReply,
            onPositionSettled = { yFrac, onRight ->
                scope.launch {
                    localRepo.saveFloatingOrbPosition(yFrac, onRight)
                }
            },
            onClick = {
                sessionRepo.setQuickSheetOpen(true)
            }
        )
    }

    if (isQuickSheetOpen) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        var inputText by remember { mutableStateOf("") }
        var attachedImage by remember { mutableStateOf<ImageBitmap?>(null) }

        val fileManager = rememberFileManager(
            callbacks = FileManagerCallbacks(
                onImagePicked = { bmp ->
                    if (bmp != null) attachedImage = bmp
                }
            )
        )

        val quickChips = remember(currentDestination) {
            when (currentDestination) {
                Destination.CumtScores -> listOf(
                    "分析我的加权绩点和成绩概况",
                    "查一下我本周有哪些课",
                    "帮我找这周哪天下午没课"
                )
                Destination.CumtExams -> listOf(
                    "汇总我接下来的考试倒计时",
                    "帮我查下考前哪几天有空复习",
                    "帮我加一门实验课"
                )
                else -> listOf(
                    "帮我看看本周哪天下午没课",
                    "查一下我今天的课程安排",
                    "帮我加一门实验课（文字叙述）",
                    "查一下我最近的考试和绩点"
                )
            }
        }

        ModalBottomSheet(
            onDismissRequest = { sessionRepo.setQuickSheetOpen(false) },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            dragHandle = null
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.76f)
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                // 顶部极简 Drag Pill + 操作栏
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(38.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        AiSparkleMiniBadge()
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "矿大 AI 助手",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = if (aiConfig.hasApiKey) {
                                    "${aiConfig.presetEnum.displayName} · ${aiConfig.model.ifBlank { "未选模型" }}"
                                } else {
                                    "尚未配置 API Key · 点击右侧进入设置"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (messages.isNotEmpty()) {
                            IconButton(
                                onClick = { sessionRepo.clearConversation() },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    vectorResource(Res.drawable.delete_24px),
                                    contentDescription = "清空对话",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        TextButton(
                            onClick = {
                                sessionRepo.setQuickSheetOpen(false)
                                onNavigate(Destination.CumtAiCourseImport)
                            }
                        ) {
                            Icon(
                                vectorResource(Res.drawable.tune_24px),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("全屏/MCP")
                        }
                        IconButton(
                            onClick = { sessionRepo.setQuickSheetOpen(false) },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                vectorResource(Res.drawable.close_24px),
                                contentDescription = "关闭",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                if (!aiConfig.hasApiKey) {
                    Surface(
                        onClick = {
                            sessionRepo.setQuickSheetOpen(false)
                            onNavigate(Destination.CumtAiCourseImport)
                        },
                        shape = RoundedCornerShape(14.dp),
                        color = CumtAccent.AMBER.containerColor(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "请先配置 API Key（支持自动识别模型）后即可对话",
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

                // 快捷指令胶囊
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SuggestionChip(
                        onClick = { fileManager.pickImage() },
                        label = { Text("📷 传实验图导课") }
                    )
                    quickChips.forEach { prompt ->
                        SuggestionChip(
                            onClick = {
                                if (prompt.contains("文字叙述")) {
                                    inputText = "帮我在第11周周六第1-2节加一门《自动控制实验》，地点信控-A205，老师周晨"
                                } else {
                                    sessionRepo.sendUserMessage(
                                        text = prompt,
                                        contextHint = contextHint,
                                        onNeedApiKey = {
                                            sessionRepo.setQuickSheetOpen(false)
                                            onNavigate(Destination.CumtAiCourseImport)
                                        }
                                    )
                                }
                            },
                            label = { Text(prompt) }
                        )
                    }
                }

                // 共享聊天消息列表
                Box(modifier = Modifier.weight(1f)) {
                    AiChatMessageList(
                        messages = messages,
                        isBusy = isBusy,
                        emptyTitle = "随时问我，不打断当前课表",
                        emptySubtitle = "直接发文字加课/调课/查空课，或点左下角图标传实验安排截图，修改后背景课表即时刷新。",
                        onConfirmAction = sessionRepo::confirmPendingAction,
                        onRejectAction = sessionRepo::rejectPendingAction,
                        onConfirmAllActions = sessionRepo::confirmAllPendingActions
                    )
                }

                // 底部多模态输入框
                AiMultimodalInputBar(
                    text = inputText,
                    onTextChange = { inputText = it },
                    attachedImage = attachedImage,
                    onPickImage = { fileManager.pickImage() },
                    onRemoveImage = { attachedImage = null },
                    isBusy = isBusy,
                    onSend = {
                        val txt = inputText
                        val img = attachedImage
                        inputText = ""
                        attachedImage = null
                        sessionRepo.sendUserMessage(
                            text = txt,
                            image = img,
                            contextHint = contextHint,
                            onNeedApiKey = {
                                sessionRepo.setQuickSheetOpen(false)
                                onNavigate(Destination.CumtAiCourseImport)
                            }
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun AiDraggableFloatingOrb(
    savedYFraction: Float,
    savedOnRight: Boolean,
    isBusy: Boolean,
    hasUnread: Boolean,
    onPositionSettled: (Float, Boolean) -> Unit,
    onClick: () -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val orbSizeDp = 50.dp
        val edgePaddingPx = with(density) { 12.dp.toPx() }
        val orbSizePx = with(density) { orbSizeDp.toPx() }
        val minYPx = with(density) { 88.dp.toPx() }
        val maxYPx = (constraints.maxHeight.toFloat() - orbSizePx - with(density) { 104.dp.toPx() })
            .coerceAtLeast(minYPx + 1f)
        val leftSnapX = edgePaddingPx
        val rightSnapX = (constraints.maxWidth.toFloat() - orbSizePx - edgePaddingPx).coerceAtLeast(leftSnapX)

        var isDragging by remember { mutableStateOf(false) }
        var targetX by remember(savedOnRight, rightSnapX) {
            mutableFloatStateOf(if (savedOnRight) rightSnapX else leftSnapX)
        }
        var targetY by remember(savedYFraction, maxYPx) {
            mutableFloatStateOf((constraints.maxHeight * savedYFraction).coerceIn(minYPx, maxYPx))
        }

        val animatedX by animateFloatAsState(
            targetValue = targetX,
            animationSpec = if (isDragging) {
                spring(stiffness = Spring.StiffnessHigh)
            } else {
                spring(dampingRatio = 0.72f, stiffness = 420f)
            },
            label = "orbX"
        )
        val animatedY by animateFloatAsState(
            targetValue = targetY,
            animationSpec = spring(dampingRatio = 0.85f, stiffness = 500f),
            label = "orbY"
        )

        val infiniteTransition = rememberInfiniteTransition(label = "orbSpin")
        val spinAngle by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1800, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "spin"
        )

        Box(
            modifier = Modifier
                .offset { IntOffset(animatedX.roundToInt(), animatedY.roundToInt()) }
                .size(orbSizeDp)
                .alpha(if (isDragging || isBusy || hasUnread) 1f else 0.92f)
                .pointerInput(leftSnapX, rightSnapX, minYPx, maxYPx) {
                    detectDragGestures(
                        onDragStart = { isDragging = true },
                        onDragEnd = {
                            isDragging = false
                            val centerX = targetX + orbSizePx / 2f
                            val snapRight = centerX >= constraints.maxWidth / 2f
                            targetX = if (snapRight) rightSnapX else leftSnapX
                            targetY = targetY.coerceIn(minYPx, maxYPx)
                            val yFrac = (targetY / constraints.maxHeight.coerceAtLeast(1)).coerceIn(0.12f, 0.85f)
                            onPositionSettled(yFrac, snapRight)
                        },
                        onDragCancel = {
                            isDragging = false
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            targetX = (targetX + dragAmount.x).coerceIn(leftSnapX, rightSnapX)
                            targetY = (targetY + dragAmount.y).coerceIn(minYPx, maxYPx)
                        }
                    )
                }
                .shadow(elevation = 8.dp, shape = CircleShape, clip = false)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color(0xFF4F46E5), Color(0xFF0E7C86))
                    )
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                // 内圈柔和微光边
                drawCircle(
                    color = Color.White.copy(alpha = 0.25f),
                    radius = size.minDimension / 2f - 1.5.dp.toPx(),
                    style = Stroke(width = 1.5.dp.toPx())
                )

                // AI 思考时的流光旋转弧
                if (isBusy) {
                    rotate(spinAngle) {
                        drawArc(
                            color = Color.White,
                            startAngle = 0f,
                            sweepAngle = 110f,
                            useCenter = false,
                            style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                }

                // Notion/AI 风格四角星芒图标
                val cx = size.width / 2f
                val cy = size.height / 2f
                val outerR = size.minDimension * 0.25f
                val innerR = outerR * 0.34f
                val mainStar = Path().apply {
                    moveTo(cx, cy - outerR)
                    quadraticTo(cx + innerR * 0.5f, cy - innerR * 0.5f, cx + outerR, cy)
                    quadraticTo(cx + innerR * 0.5f, cy + innerR * 0.5f, cx, cy + outerR)
                    quadraticTo(cx - innerR * 0.5f, cy + innerR * 0.5f, cx - outerR, cy)
                    quadraticTo(cx - innerR * 0.5f, cy - innerR * 0.5f, cx, cy - outerR)
                    close()
                }
                drawPath(mainStar, color = Color.White)

                // 右上角小伴星
                val sx = cx + outerR * 0.85f
                val sy = cy - outerR * 0.85f
                val sr = outerR * 0.32f
                val miniStar = Path().apply {
                    moveTo(sx, sy - sr)
                    lineTo(sx + sr * 0.35f, sy - sr * 0.35f)
                    lineTo(sx + sr, sy)
                    lineTo(sx + sr * 0.35f, sy + sr * 0.35f)
                    lineTo(sx, sy + sr)
                    lineTo(sx - sr * 0.35f, sy + sr * 0.35f)
                    lineTo(sx - sr, sy)
                    lineTo(sx - sr * 0.35f, sy - sr * 0.35f)
                    close()
                }
                drawPath(miniStar, color = Color.White.copy(alpha = 0.88f))
            }

            if (hasUnread) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF34D399))
                )
            }
        }
    }
}

@Composable
internal fun AiSparkleMiniBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(34.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF4F46E5), Color(0xFF0E7C86))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(20.dp)) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val r = size.minDimension * 0.42f
            val ir = r * 0.32f
            val p = Path().apply {
                moveTo(cx, cy - r)
                quadraticTo(cx + ir * 0.5f, cy - ir * 0.5f, cx + r, cy)
                quadraticTo(cx + ir * 0.5f, cy + ir * 0.5f, cx, cy + r)
                quadraticTo(cx - ir * 0.5f, cy + ir * 0.5f, cx - r, cy)
                quadraticTo(cx - ir * 0.5f, cy - ir * 0.5f, cx, cy - r)
                close()
            }
            drawPath(p, color = Color.White)
        }
    }
}

@Composable
internal fun AiChatMessageList(
    messages: List<CumtAiChatMessage>,
    isBusy: Boolean,
    emptyTitle: String,
    emptySubtitle: String,
    onConfirmAction: (String, String) -> Unit,
    onRejectAction: (String, String) -> Unit,
    onConfirmAllActions: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size, isBusy) {
        val total = messages.size + if (isBusy) 1 else 0
        if (total > 0) {
            listState.animateScrollToItem(total - 1)
        }
    }

    if (messages.isEmpty() && !isBusy) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AiSparkleMiniBadge(modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(4.dp))
                Text(
                    text = emptyTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = emptySubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(messages, key = { it.id }) { msg ->
            AiChatBubbleItem(
                message = msg,
                onConfirmAction = { actId -> onConfirmAction(msg.id, actId) },
                onRejectAction = { actId -> onRejectAction(msg.id, actId) },
                onConfirmAll = { onConfirmAllActions(msg.id) }
            )
        }

        if (isBusy) {
            item(key = "ai_loading") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Text(
                        text = "AI 正在思考并调用课表工具...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun AiChatBubbleItem(
    message: CumtAiChatMessage,
    onConfirmAction: (String) -> Unit,
    onRejectAction: (String) -> Unit,
    onConfirmAll: () -> Unit
) {
    val isUser = message.role == "user"
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isUser) 18.dp else 6.dp,
                bottomEnd = if (isUser) 6.dp else 18.dp
            ),
            color = when {
                message.isError -> CumtAccent.CORAL.containerColor()
                isUser -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.surfaceContainerHigh
            },
            modifier = Modifier.widthIn(max = 340.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (message.hasImage && message.imageNote.isNotBlank()) {
                    CumtStatusPill(
                        text = "📷 ${message.imageNote}",
                        containerColor = Color.White.copy(alpha = 0.2f),
                        contentColor = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                    )
                }
                if (message.toolTrace.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        message.toolTrace.forEach { trace ->
                            Text(
                                text = trace,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = when {
                        message.isError -> CumtAccent.CORAL.contentColor()
                        isUser -> MaterialTheme.colorScheme.onPrimary
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
            }
        }

        // 待确认或已执行的写操作卡片
        if (message.pendingActions.isNotEmpty()) {
            val pendingCount = message.pendingActions.count { it.status == CumtAiActionStatus.PENDING }
            Column(
                modifier = Modifier.fillMaxWidth(0.95f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                message.pendingActions.forEach { action ->
                    AiPendingActionCard(
                        action = action,
                        onConfirm = { onConfirmAction(action.id) },
                        onReject = { onRejectAction(action.id) }
                    )
                }
                if (pendingCount > 1) {
                    Button(
                        onClick = onConfirmAll,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            vectorResource(Res.drawable.check_circle_24px),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("一键全部应用（$pendingCount 项）", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun AiPendingActionCard(
    action: CumtAiPendingAction,
    onConfirm: () -> Unit,
    onReject: () -> Unit
) {
    val accent = when (action.status) {
        CumtAiActionStatus.PENDING -> CumtAccent.VIOLET
        CumtAiActionStatus.APPLIED -> CumtAccent.EMERALD
        CumtAiActionStatus.REJECTED -> CumtAccent.SLATE
        CumtAiActionStatus.FAILED -> CumtAccent.CORAL
    }
    val statusLabel = when (action.status) {
        CumtAiActionStatus.PENDING -> "待确认"
        CumtAiActionStatus.APPLIED -> "已应用"
        CumtAiActionStatus.REJECTED -> "已取消"
        CumtAiActionStatus.FAILED -> "执行失败"
    }

    CumtSurfaceCard(
        cornerRadius = 16.dp,
        borderColor = accent.contentColor().copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = action.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                CumtStatusPill(
                    text = statusLabel,
                    containerColor = accent.containerColor(),
                    contentColor = accent.contentColor()
                )
            }
            if (action.summary.isNotBlank()) {
                Text(
                    text = action.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (action.detailLines.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        action.detailLines.take(12).forEach { line ->
                            Text(
                                text = "• $line",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        if (action.detailLines.size > 12) {
                            Text(
                                text = "... 等共 ${action.detailLines.size} 条",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            if (action.resultMessage.isNotBlank()) {
                Text(
                    text = action.resultMessage,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = accent.contentColor()
                )
            }
            if (action.status == CumtAiActionStatus.PENDING) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onReject,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("取消")
                    }
                    Button(
                        onClick = onConfirm,
                        modifier = Modifier.weight(1.4f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            vectorResource(Res.drawable.check_circle_24px),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("确认应用", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
internal fun AiMultimodalInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    attachedImage: ImageBitmap?,
    onPickImage: () -> Unit,
    onRemoveImage: () -> Unit,
    isBusy: Boolean,
    onSend: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (attachedImage != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    ) {
                        Image(
                            bitmap = attachedImage,
                            contentDescription = "待发送图片",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Text(
                        text = "已附加图片（${attachedImage.width}×${attachedImage.height}）",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onRemoveImage, modifier = Modifier.size(30.dp)) {
                        Icon(
                            vectorResource(Res.drawable.close_24px),
                            contentDescription = "移除图片",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IconButton(
                    onClick = onPickImage,
                    enabled = !isBusy,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        vectorResource(Res.drawable.image_24px),
                        contentDescription = "添加实验表或课表图片",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChange,
                    placeholder = { Text("输入文字加/调/查课，或发实验安排图...") },
                    maxLines = 4,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f)
                )

                FilledIconButton(
                    onClick = onSend,
                    enabled = !isBusy && (text.isNotBlank() || attachedImage != null),
                    modifier = Modifier.size(46.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(
                            vectorResource(Res.drawable.arrow_forward_24px),
                            contentDescription = "发送"
                        )
                    }
                }
            }
        }
    }
}
