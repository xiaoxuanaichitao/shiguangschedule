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
import androidx.compose.foundation.border
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
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.xingheyuzhuan.shiguangschedule.Destination
import com.xingheyuzhuan.shiguangschedule.cumt.CumtAiActionStatus
import com.xingheyuzhuan.shiguangschedule.cumt.CumtAiChatMessage
import com.xingheyuzhuan.shiguangschedule.cumt.CumtAiConfig
import com.xingheyuzhuan.shiguangschedule.cumt.CumtAiOrbIconStyle
import com.xingheyuzhuan.shiguangschedule.cumt.CumtAiPendingAction
import com.xingheyuzhuan.shiguangschedule.cumt.CumtAiSessionRepository
import com.xingheyuzhuan.shiguangschedule.cumt.CumtLocalRepository
import com.xingheyuzhuan.shiguangschedule.cumt.ShiguangMcpServerManager
import com.xingheyuzhuan.shiguangschedule.tool.FileManagerCallbacks
import com.xingheyuzhuan.shiguangschedule.tool.rememberFileManager
import com.xingheyuzhuan.shiguangschedule.ui.theme.LocalIsDarkTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.vectorResource
import org.koin.compose.koinInject
import shiguangschedule.shared.generated.resources.Res
import shiguangschedule.shared.generated.resources.ai_empty_welcome
import shiguangschedule.shared.generated.resources.ai_orb_bubbles
import shiguangschedule.shared.generated.resources.ai_orb_mascot
import shiguangschedule.shared.generated.resources.arrow_upward_24px
import shiguangschedule.shared.generated.resources.check_circle_24px
import shiguangschedule.shared.generated.resources.close_24px
import shiguangschedule.shared.generated.resources.delete_24px
import shiguangschedule.shared.generated.resources.image_24px
import shiguangschedule.shared.generated.resources.info_24px
import shiguangschedule.shared.generated.resources.tune_24px
import kotlin.math.roundToInt

/**
 * 全局 Notion 风格 AI 悬浮小球 + 半屏灵动对话抽屉。
 * 支持尺寸调节、闲置透明度、3 秒自动贴边半隐、双内置手绘图标与相册自选头像。
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

    LaunchedEffect(Unit) {
        mcpManager.ensureAutoStartIfEnabled()
    }

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
            aiConfig = aiConfig,
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
            shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
            dragHandle = null
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(aiConfig.quickSheetHeightFraction.coerceIn(0.55f, 0.92f))
                    .navigationBarsPadding()
                    .imePadding()
            ) {
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
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        AiOrbAvatarSurface(
                            aiConfig = aiConfig,
                            size = 36.dp
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "矿大 AI 助手",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = if (aiConfig.hasApiKey) {
                                    "${aiConfig.effectiveDisplayName} · ${aiConfig.model.ifBlank { "未选模型" }}"
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

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (messages.isNotEmpty()) {
                            Surface(
                                onClick = { sessionRepo.clearConversation() },
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f),
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        vectorResource(Res.drawable.delete_24px),
                                        contentDescription = "清空对话",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        Surface(
                            onClick = {
                                sessionRepo.setQuickSheetOpen(false)
                                onNavigate(Destination.CumtAiCourseImport)
                            },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    vectorResource(Res.drawable.tune_24px),
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "设置",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        Surface(
                            onClick = { sessionRepo.setQuickSheetOpen(false) },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    vectorResource(Res.drawable.close_24px),
                                    contentDescription = "关闭",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
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
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        icon = {
                            Icon(
                                vectorResource(Res.drawable.image_24px),
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        label = { Text("传实验图导课", style = MaterialTheme.typography.labelMedium) }
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
                            shape = CircleShape,
                            border = chipBorder,
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            ),
                            label = { Text(prompt, style = MaterialTheme.typography.labelMedium) }
                        )
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    AiChatMessageList(
                        messages = messages,
                        isBusy = isBusy,
                        emptyTitle = "随时问我，不打断当前课表",
                        emptySubtitle = "发文字加课/调课/查空课，或点左下角传实验安排截图，确认后背景课表实时刷新。",
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

/**
 * 极简 Notion 风格圆形头像/悬浮球渲染组件（自动适配浅色/深色模式与自选图片）。
 */
@Composable
internal fun AiOrbAvatarSurface(
    aiConfig: CumtAiConfig,
    size: Dp,
    modifier: Modifier = Modifier,
    isBusy: Boolean = false,
    hasUnread: Boolean = false
) {
    val isDark = LocalIsDarkTheme.current
    val bgColor = if (isDark) Color(0xFF22252A) else Color(0xFFFFFFFF)
    val borderColor = if (isDark) Color.White.copy(alpha = 0.16f) else Color(0x1C000000)
    val inkTint = if (isDark) Color(0xFFF1F3F5) else Color(0xFF181A1E)
    val accentRingColor = MaterialTheme.colorScheme.primary

    val infiniteTransition = rememberInfiniteTransition(label = "orbSpin")
    val spinAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spin"
    )

    Box(
        modifier = modifier
            .size(size)
            .shadow(elevation = 6.dp, shape = CircleShape, clip = false)
            .clip(CircleShape)
            .background(bgColor)
            .border(1.dp, borderColor, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        val style = aiConfig.orbIconStyleEnum
        if (style == CumtAiOrbIconStyle.CUSTOM && aiConfig.floatingOrbCustomImagePath.isNotBlank()) {
            AsyncImage(
                model = aiConfig.floatingOrbCustomImagePath,
                contentDescription = "AI 悬浮球自定义图标",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
            )
        } else {
            val drawableRes = when (style) {
                CumtAiOrbIconStyle.MASCOT -> Res.drawable.ai_orb_mascot
                else -> Res.drawable.ai_orb_bubbles
            }
            Image(
                painter = painterResource(drawableRes),
                contentDescription = "矿大 AI 助手",
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.tint(inkTint),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(size * 0.13f)
            )
        }

        if (isBusy) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                rotate(spinAngle) {
                    drawArc(
                        color = accentRingColor,
                        startAngle = 0f,
                        sweepAngle = 105f,
                        useCenter = false,
                        style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }
        }

        if (hasUnread) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(size * 0.10f)
                    .size((size * 0.20f).coerceIn(8.dp, 12.dp))
                    .clip(CircleShape)
                    .background(Color(0xFF10B981))
            )
        }
    }
}

@Composable
private fun AiDraggableFloatingOrb(
    aiConfig: CumtAiConfig,
    isBusy: Boolean,
    hasUnread: Boolean,
    onPositionSettled: (Float, Boolean) -> Unit,
    onClick: () -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val orbSizeDp = aiConfig.floatingOrbSizeDp.coerceIn(36f, 68f).dp
        val edgePaddingPx = with(density) { 12.dp.toPx() }
        val orbSizePx = with(density) { orbSizeDp.toPx() }
        val minYPx = with(density) { 88.dp.toPx() }
        val maxYPx = (constraints.maxHeight.toFloat() - orbSizePx - with(density) { 104.dp.toPx() })
            .coerceAtLeast(minYPx + 1f)
        val leftSnapX = edgePaddingPx
        val rightSnapX = (constraints.maxWidth.toFloat() - orbSizePx - edgePaddingPx).coerceAtLeast(leftSnapX)

        var isDragging by remember { mutableStateOf(false) }
        var isOnRight by remember(aiConfig.floatingOrbOnRight) { mutableStateOf(aiConfig.floatingOrbOnRight) }
        var interactionTick by remember { mutableIntStateOf(0) }
        var isIdlePeeked by remember { mutableStateOf(false) }

        var dragX by remember(aiConfig.floatingOrbOnRight, rightSnapX) {
            mutableFloatStateOf(if (aiConfig.floatingOrbOnRight) rightSnapX else leftSnapX)
        }
        var targetY by remember(aiConfig.floatingOrbYFraction, maxYPx) {
            mutableFloatStateOf((constraints.maxHeight * aiConfig.floatingOrbYFraction).coerceIn(minYPx, maxYPx))
        }

        // 闲置 3 秒后自动贴边半隐（推理中、有未读回复或关闭自动半隐时保持完整展示）
        LaunchedEffect(interactionTick, isDragging, isBusy, hasUnread, aiConfig.floatingOrbAutoPeek) {
            isIdlePeeked = false
            if (!isDragging && !isBusy && !hasUnread && aiConfig.floatingOrbAutoPeek) {
                delay(3000L)
                isIdlePeeked = true
            }
        }

        val resolvedTargetX = when {
            isDragging -> dragX
            isIdlePeeked && isOnRight -> constraints.maxWidth.toFloat() - orbSizePx * 0.46f
            isIdlePeeked && !isOnRight -> -orbSizePx * 0.54f
            isOnRight -> rightSnapX
            else -> leftSnapX
        }

        val animatedX by animateFloatAsState(
            targetValue = resolvedTargetX,
            animationSpec = if (isDragging) {
                spring(stiffness = Spring.StiffnessHigh)
            } else {
                spring(dampingRatio = 0.76f, stiffness = 380f)
            },
            label = "orbX"
        )
        val animatedY by animateFloatAsState(
            targetValue = targetY,
            animationSpec = spring(dampingRatio = 0.85f, stiffness = 500f),
            label = "orbY"
        )
        val animatedAlpha by animateFloatAsState(
            targetValue = if (isDragging || isBusy || hasUnread || !isIdlePeeked) {
                1f
            } else {
                aiConfig.floatingOrbIdleAlpha.coerceIn(0.35f, 1.0f)
            },
            animationSpec = tween(260),
            label = "orbAlpha"
        )

        Box(
            modifier = Modifier
                .offset { IntOffset(animatedX.roundToInt(), animatedY.roundToInt()) }
                .alpha(animatedAlpha)
                .pointerInput(leftSnapX, rightSnapX, minYPx, maxYPx) {
                    detectDragGestures(
                        onDragStart = {
                            dragX = resolvedTargetX.coerceIn(leftSnapX, rightSnapX)
                            isDragging = true
                            isIdlePeeked = false
                            interactionTick++
                        },
                        onDragEnd = {
                            isDragging = false
                            val centerX = dragX + orbSizePx / 2f
                            val snapRight = centerX >= constraints.maxWidth / 2f
                            isOnRight = snapRight
                            dragX = if (snapRight) rightSnapX else leftSnapX
                            targetY = targetY.coerceIn(minYPx, maxYPx)
                            interactionTick++
                            val yFrac = (targetY / constraints.maxHeight.coerceAtLeast(1)).coerceIn(0.12f, 0.85f)
                            onPositionSettled(yFrac, snapRight)
                        },
                        onDragCancel = {
                            isDragging = false
                            interactionTick++
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            dragX = (dragX + dragAmount.x).coerceIn(leftSnapX, rightSnapX)
                            targetY = (targetY + dragAmount.y).coerceIn(minYPx, maxYPx)
                        }
                    )
                }
                .clickable {
                    interactionTick++
                    if (isIdlePeeked) {
                        // 半隐状态下首次轻触先优雅探出完整悬浮球，防边缘误触直接弹大面板
                        isIdlePeeked = false
                    } else {
                        onClick()
                    }
                }
        ) {
            AiOrbAvatarSurface(
                aiConfig = aiConfig,
                size = orbSizeDp,
                isBusy = isBusy,
                hasUnread = hasUnread
            )
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
    onUndoAction: (String, String) -> Unit = { _, _ -> },
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
        val inkColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f)
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Image(
                    painter = painterResource(Res.drawable.ai_empty_welcome),
                    contentDescription = "AI 助手欢迎插画",
                    contentScale = ContentScale.Fit,
                    colorFilter = ColorFilter.tint(inkColor),
                    modifier = Modifier.size(136.dp)
                )
                Text(
                    text = emptyTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = emptySubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
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
                onConfirmAll = { onConfirmAllActions(msg.id) },
                onUndoAction = { actId -> onUndoAction(msg.id, actId) }
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
    onConfirmAll: () -> Unit,
    onUndoAction: (String) -> Unit
) {
    val isUser = message.role == "user"
    val contentColor = when {
        message.isError -> CumtAccent.CORAL.contentColor()
        isUser -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurface
    }
    val codeBgColor = if (isUser) {
        Color.White.copy(alpha = 0.18f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 20.dp,
                topEnd = 20.dp,
                bottomStart = if (isUser) 20.dp else 6.dp,
                bottomEnd = if (isUser) 6.dp else 20.dp
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
                        text = message.imageNote,
                        icon = Res.drawable.image_24px,
                        containerColor = if (isUser) {
                            Color.White.copy(alpha = 0.2f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        },
                        contentColor = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                    )
                }
                if (message.toolTrace.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        message.toolTrace.forEach { trace ->
                            val cleanTrace = trace
                                .removePrefix("🔧")
                                .removePrefix("✅")
                                .removePrefix("⚡")
                                .trim()
                            CumtStatusPill(
                                text = cleanTrace,
                                dotColor = MaterialTheme.colorScheme.primary,
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.72f),
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                if (isUser || message.isError) {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 21.sp),
                        color = contentColor
                    )
                } else {
                    AiLightweightMarkdownText(
                        rawText = message.text,
                        textColor = contentColor,
                        codeBgColor = codeBgColor
                    )
                }
            }
        }

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
                        onReject = { onRejectAction(action.id) },
                        onUndo = { onUndoAction(action.id) }
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

/**
 * 轻量级 Markdown 排版渲染器：支持代码块、标题、无序列表、**加粗** 与 `行内代码`。
 */
@Composable
internal fun AiLightweightMarkdownText(
    rawText: String,
    textColor: Color,
    codeBgColor: Color
) {
    val segments = remember(rawText) { splitMarkdownCodeBlocks(rawText) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        segments.forEach { (isCode, body) ->
            if (isCode) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = codeBgColor,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = body.trim(),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = textColor,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            } else {
                val annotated = remember(body, codeBgColor) {
                    parseInlineMarkdown(body, codeBgColor)
                }
                Text(
                    text = annotated,
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                    color = textColor
                )
            }
        }
    }
}

internal fun splitMarkdownCodeBlocks(input: String): List<Pair<Boolean, String>> {
    val fenceRegex = Regex("""```[a-zA-Z0-9_-]*\n?([\s\S]*?)```""")
    val result = mutableListOf<Pair<Boolean, String>>()
    var lastIdx = 0
    fenceRegex.findAll(input).forEach { m ->
        val before = input.substring(lastIdx, m.range.first).trim()
        if (before.isNotEmpty()) result.add(false to before)
        val code = m.groupValues[1].trim()
        if (code.isNotEmpty()) result.add(true to code)
        lastIdx = m.range.last + 1
    }
    val tail = input.substring(lastIdx).trim()
    if (tail.isNotEmpty()) result.add(false to tail)
    return if (result.isEmpty()) listOf(false to input) else result
}

internal fun parseInlineMarkdown(text: String, codeBg: Color): AnnotatedString = buildAnnotatedString {
    val lines = text.lines()
    lines.forEachIndexed { lineIndex, rawLine ->
        val trimmed = rawLine.trimStart()
        val isHeading = trimmed.startsWith("### ") || trimmed.startsWith("## ") || trimmed.startsWith("# ")
        val contentLine = when {
            trimmed.startsWith("### ") -> trimmed.removePrefix("### ")
            trimmed.startsWith("## ") -> trimmed.removePrefix("## ")
            trimmed.startsWith("# ") -> trimmed.removePrefix("# ")
            trimmed.startsWith("- ") -> "• ${trimmed.removePrefix("- ")}"
            trimmed.startsWith("* ") -> "• ${trimmed.removePrefix("* ")}"
            else -> rawLine
        }

        if (isHeading) {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                appendInlineSpans(contentLine, codeBg)
            }
        } else {
            appendInlineSpans(contentLine, codeBg)
        }
        if (lineIndex < lines.lastIndex) append("\n")
    }
}

private fun AnnotatedString.Builder.appendInlineSpans(line: String, codeBg: Color) {
    val tokenRegex = Regex("""\*\*(.+?)\*\*|`([^`]+)`""")
    var cursor = 0
    tokenRegex.findAll(line).forEach { match ->
        if (match.range.first > cursor) {
            append(line.substring(cursor, match.range.first))
        }
        val boldGroup = match.groupValues[1]
        val codeGroup = match.groupValues[2]
        if (boldGroup.isNotEmpty()) {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                append(boldGroup)
            }
        } else if (codeGroup.isNotEmpty()) {
            withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBg)) {
                append(" $codeGroup ")
            }
        }
        cursor = match.range.last + 1
    }
    if (cursor < line.length) {
        append(line.substring(cursor))
    }
}

@Composable
private fun AiPendingActionCard(
    action: CumtAiPendingAction,
    onConfirm: () -> Unit,
    onReject: () -> Unit,
    onUndo: () -> Unit
) {
    val accent = when (action.status) {
        CumtAiActionStatus.PENDING -> if (action.conflictWarnings.isNotEmpty()) CumtAccent.AMBER else CumtAccent.VIOLET
        CumtAiActionStatus.APPLIED -> CumtAccent.EMERALD
        CumtAiActionStatus.UNDONE -> CumtAccent.SLATE
        CumtAiActionStatus.REJECTED -> CumtAccent.SLATE
        CumtAiActionStatus.FAILED -> CumtAccent.CORAL
    }
    val statusLabel = when (action.status) {
        CumtAiActionStatus.PENDING -> if (action.conflictWarnings.isNotEmpty()) "待确认·有重叠" else "待确认"
        CumtAiActionStatus.APPLIED -> "已应用"
        CumtAiActionStatus.UNDONE -> "已撤回"
        CumtAiActionStatus.REJECTED -> "已取消"
        CumtAiActionStatus.FAILED -> "执行失败"
    }

    CumtSurfaceCard(
        cornerRadius = 18.dp,
        borderColor = accent.contentColor().copy(alpha = 0.30f),
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
            if (action.conflictWarnings.isNotEmpty() && action.status == CumtAiActionStatus.PENDING) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = CumtAccent.AMBER.containerColor(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                vectorResource(Res.drawable.info_24px),
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = CumtAccent.AMBER.contentColor()
                            )
                            Text(
                                text = "时间冲突提醒",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = CumtAccent.AMBER.contentColor()
                            )
                        }
                        action.conflictWarnings.forEach { warn ->
                            Text(
                                text = "• $warn",
                                style = MaterialTheme.typography.labelSmall,
                                color = CumtAccent.AMBER.contentColor()
                            )
                        }
                    }
                }
            }
            if (action.resultMessage.isNotBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = action.resultMessage,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = accent.contentColor(),
                        modifier = Modifier.weight(1f)
                    )
                    if (action.status == CumtAiActionStatus.APPLIED && action.createdCourseIds.isNotEmpty()) {
                        TextButton(onClick = onUndo) {
                            Text("撤回导入", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
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
    onCancelBusy: () -> Unit = {},
    onSend: () -> Unit,
    modifier: Modifier = Modifier
) {
    val canSend = text.isNotBlank() || attachedImage != null
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.78f),
            border = BorderStroke(
                width = 0.8.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (attachedImage != null) {
                    Row(
                        modifier = Modifier.padding(start = 6.dp, end = 4.dp, top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(RoundedCornerShape(12.dp))
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
                            text = "已附加图片 · ${attachedImage.width}×${attachedImage.height}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = onRemoveImage, modifier = Modifier.size(28.dp)) {
                            Icon(
                                vectorResource(Res.drawable.close_24px),
                                contentDescription = "移除图片",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        onClick = onPickImage,
                        enabled = !isBusy,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                vectorResource(Res.drawable.image_24px),
                                contentDescription = "添加实验表或课表图片",
                                modifier = Modifier.size(19.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    BasicTextField(
                        value = text,
                        onValueChange = onTextChange,
                        maxLines = 4,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 20.sp
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .weight(1f)
                            .align(Alignment.CenterVertically)
                            .padding(horizontal = 4.dp, vertical = 7.dp),
                        decorationBox = { innerTextField ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (text.isEmpty()) {
                                    Text(
                                        text = "输入加/调/查课指令，或上传实验图…",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )

                    if (isBusy) {
                        FilledIconButton(
                            onClick = onCancelBusy,
                            modifier = Modifier.size(38.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        ) {
                            Icon(
                                vectorResource(Res.drawable.close_24px),
                                contentDescription = "停止生成",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else {
                        FilledIconButton(
                            onClick = onSend,
                            enabled = canSend,
                            modifier = Modifier.size(38.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.onSurface,
                                contentColor = MaterialTheme.colorScheme.surface,
                                disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
                                disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.32f)
                            )
                        ) {
                            Icon(
                                vectorResource(Res.drawable.arrow_upward_24px),
                                contentDescription = "发送",
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
