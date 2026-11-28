package com.xingheyuzhuan.shiguangschedule.ui.cumt

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.xingheyuzhuan.shiguangschedule.Destination
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.vectorResource
import shiguangschedule.shared.generated.resources.Res
import shiguangschedule.shared.generated.resources.calendar_today_24px
import shiguangschedule.shared.generated.resources.explore_24px
import shiguangschedule.shared.generated.resources.palette_24px
import shiguangschedule.shared.generated.resources.school_24px

private data class OnboardingSlide(
    val stepLabel: String,
    val title: String,
    val description: String,
    val tips: List<String>,
    val icon: DrawableResource,
    val accent: CumtAccent,
    val actionLabel: String?,
    val targetDestination: Destination?
)

/**
 * 新用户首次进入交互式新手教学弹窗（适配手机与平板，支持跳过与一键直达对应功能）。
 */
@Composable
fun CumtOnboardingDialog(
    onDismiss: () -> Unit,
    onNavigateAndClose: (Destination) -> Unit
) {
    val slides = listOf(
        OnboardingSlide(
            stepLabel = "第 1 步 · 导入课表",
            title = "一键同步矿大教务课表",
            description = "绑定矿大统一身份认证后，可自动导入本科 / 研究生正方课表，并自动补全学期与周次。",
            tips = listOf(
                "入口：底部「校园」→「教务导课表」",
                "支持创建多张课表，并在周课表右上角一键切换",
                "长按任意课程卡片可拖动调课，点击空位即可快速加课"
            ),
            icon = Res.drawable.school_24px,
            accent = CumtAccent.INDIGO,
            actionLabel = "立即去导课",
            targetDestination = Destination.CumtCourseImport
        ),
        OnboardingSlide(
            stepLabel = "第 2 步 · 提醒与系统日历",
            title = "同步到系统日历，上课不错过",
            description = "开启「同步到手机系统日历」与「课程提醒」，在锁屏、负一屏、手表上也能随时查看下一节课。",
            tips = listOf(
                "入口：「我的 / 设置」→「课程提醒与日历同步」",
                "支持自定义提前提醒分钟数与静音/免打扰模式",
                "考试倒计时同样支持一键同步写入课表"
            ),
            icon = Res.drawable.calendar_today_24px,
            accent = CumtAccent.EMERALD,
            actionLabel = "去开启日历与提醒",
            targetDestination = Destination.NotificationSettings
        ),
        OnboardingSlide(
            stepLabel = "第 3 步 · 个性化与壁纸",
            title = "自由定制壁纸与课表样式",
            description = "支持分别设置浅色/深色自定义背景，并可自由勾选周课表、今日课表、校园、我的各自是否同步壁纸。",
            tips = listOf(
                "入口：周课表右上角调色盘图标，或「我的」顶部第一张卡片",
                "可微调格子高度、表头高度、圆角、卡片透明度与背景模糊",
                "全局软件字号与课表网格字号互不干扰"
            ),
            icon = Res.drawable.palette_24px,
            accent = CumtAccent.VIOLET,
            actionLabel = "去换壁纸与样式",
            targetDestination = Destination.StyleSettings
        ),
        OnboardingSlide(
            stepLabel = "第 4 步 · 矿大百宝箱 & AI 助手",
            title = "校园服务与智能课表助手",
            description = "一卡通余额、南湖/文昌宿舍电量、成绩绩点、图书续借、校车倒计时一站搞定；还内置悬浮 AI 课表助手。",
            tips = listOf(
                "AI 助手支持接入 DeepSeek / OpenAI / Gemini 或自定义接口",
                "发文字或实验安排表截图即可自动识别并生成加课/调课卡片",
                "之后随时可在「我的 → 更多选项 → 新手使用引导」重看本教程"
            ),
            icon = Res.drawable.explore_24px,
            accent = CumtAccent.OCEAN,
            actionLabel = null,
            targetDestination = null
        )
    )

    val pagerState = rememberPagerState(pageCount = { slides.size })
    val scope = rememberCoroutineScope()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .widthIn(max = 460.dp),
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 顶部标题与跳过按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "欢迎使用矿大时光课表",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                    TextButton(
                        onClick = onDismiss,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "跳过",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 滑动教程页
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxWidth()
                ) { page ->
                    val slide = slides[page]
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CumtStatusPill(
                            text = slide.stepLabel,
                            containerColor = slide.accent.containerColor(),
                            contentColor = slide.accent.contentColor()
                        )

                        CumtIconBadge(
                            icon = slide.icon,
                            accent = slide.accent,
                            size = 56.dp,
                            iconSize = 28.dp,
                            cornerRadius = 18.dp
                        )

                        Text(
                            text = slide.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = slide.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                slide.tips.forEach { tip ->
                                    Row(
                                        verticalAlignment = Alignment.Top,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .padding(top = 6.dp)
                                                .size(5.dp)
                                                .clip(CircleShape)
                                                .background(slide.accent.contentColor())
                                        )
                                        Text(
                                            text = tip,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f),
                                            lineHeight = 18.sp
                                        )
                                    }
                                }
                            }
                        }

                        if (slide.actionLabel != null && slide.targetDestination != null) {
                            OutlinedButton(
                                onClick = { onNavigateAndClose(slide.targetDestination) },
                                shape = CircleShape,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(slide.actionLabel, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                // 底部指示点 + 上一步/下一步按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        repeat(slides.size) { idx ->
                            val selected = pagerState.currentPage == idx
                            Box(
                                modifier = Modifier
                                    .height(7.dp)
                                    .width(if (selected) 20.dp else 7.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outlineVariant
                                    )
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (pagerState.currentPage > 0) {
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                    }
                                }
                            ) {
                                Text("上一步")
                            }
                        }
                        val isLast = pagerState.currentPage == slides.lastIndex
                        Button(
                            onClick = {
                                if (isLast) {
                                    onDismiss()
                                } else {
                                    scope.launch {
                                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                    }
                                }
                            },
                            shape = CircleShape
                        ) {
                            Text(if (isLast) "开始使用" else "下一步", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}
