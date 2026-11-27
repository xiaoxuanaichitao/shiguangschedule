package com.xingheyuzhuan.shiguangschedule.ui.schedule.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

/**
 * 统一的课表自定义背景与液态毛玻璃渲染参数（周课表、当日课表、个性化预览页三端共享）。
 */
@Immutable
data class WallpaperVisualConfig(
    val wallpaperPath: String? = null,
    val blurDp: Float = 0f,
    val dimAlpha: Float = 0.06f,
    val glassEnabled: Boolean = true,
    val cardFrost: Float = 0.28f
) {
    val hasWallpaper: Boolean
        get() = !wallpaperPath.isNullOrBlank()
}

/**
 * 壁纸模式下的页面文字微阴影（确保白色表头、节次时间在亮色天空/二次元亮底壁纸上依然锐利清晰）。
 */
val WallpaperHeaderTextShadow = Shadow(
    color = Color(0x88000000),
    offset = Offset(0f, 1.8f),
    blurRadius = 4.5f
)

val WallpaperCardTextShadow = Shadow(
    color = Color(0x52000000),
    offset = Offset(0f, 1.2f),
    blurRadius = 3.0f
)

/**
 * 全屏沉浸式自定义壁纸背景层：
 * 1. 支持实时高斯模糊（边缘自动微放大消除白边光晕）；
 * 2. 全局护眼柔暗遮罩；
 * 3. 顶栏与星期栏渐进式阅读暗角 + 底部导航栏轻柔托底，彻底解决深/浅复杂壁纸吃字问题。
 */
@Composable
fun ScheduleWallpaperBackdrop(
    visuals: WallpaperVisualConfig,
    modifier: Modifier = Modifier,
    showTopScrim: Boolean = true,
    showBottomScrim: Boolean = true
) {
    val path = visuals.wallpaperPath?.takeIf { it.isNotBlank() } ?: return
    val clampedBlur = visuals.blurDp.coerceIn(0f, 28f)
    val edgeScale = if (clampedBlur > 0.2f) 1f + (clampedBlur / 160f) else 1f
    val clampedDim = visuals.dimAlpha.coerceIn(0f, 0.60f)

    Box(modifier = modifier.fillMaxSize()) {
        AsyncImage(
            model = path,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = edgeScale
                    scaleY = edgeScale
                    clip = true
                }
                .let { mod ->
                    if (clampedBlur > 0.2f) {
                        mod.blur(clampedBlur.dp, BlurredEdgeTreatment.Rectangle)
                    } else {
                        mod
                    }
                }
        )

        if (clampedDim > 0.005f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = clampedDim))
            )
        }

        if (showTopScrim) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(152.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            0.0f to Color.Black.copy(alpha = 0.40f),
                            0.56f to Color.Black.copy(alpha = 0.18f),
                            1.0f to Color.Transparent
                        )
                    )
            )
        }

        if (showBottomScrim) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(92.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            0.0f to Color.Transparent,
                            1.0f to Color.Black.copy(alpha = 0.24f)
                        )
                    )
            )
        }
    }
}

/**
 * 课程卡片果冻毛玻璃 / 液态玻璃质感修饰器：
 * - 混合柔和雾白防花底（cardFrost），避免壁纸细碎线条穿透后干扰文字；
 * - 叠加对角线微折射光泽与 0.9dp 镜面高光内描边（左上亮白 → 中段微透 → 右下柔白）。
 */
fun Modifier.scheduleGlassCardSurface(
    baseColor: Color,
    alpha: Float,
    cornerRadius: Dp,
    hasWallpaper: Boolean,
    glassEnabled: Boolean,
    cardFrost: Float,
    customBorderApplied: Boolean = false
): Modifier {
    val shape = RoundedCornerShape(cornerRadius)
    val clampedAlpha = alpha.coerceIn(0.10f, 1.0f)

    if (!hasWallpaper && !glassEnabled) {
        val top = baseColor.copy(alpha = clampedAlpha)
        val bottom = baseColor.copy(alpha = (clampedAlpha * 0.92f).coerceIn(0.10f, 1.0f))
        return this
            .clip(shape)
            .background(Brush.verticalGradient(listOf(top, bottom)))
    }

    val frostRatio = if (hasWallpaper) (cardFrost.coerceIn(0f, 0.85f) * 0.24f) else 0f
    val frostedBase = lerp(baseColor, Color.White, frostRatio)
    val topFill = lerp(frostedBase, Color.White, if (glassEnabled) 0.10f else 0.03f)
        .copy(alpha = clampedAlpha)
    val bottomFill = frostedBase
        .copy(alpha = (clampedAlpha * 0.88f).coerceIn(0.12f, 1.0f))

    val rimBrush = Brush.linearGradient(
        colors = if (glassEnabled) {
            listOf(
                Color.White.copy(alpha = 0.62f),
                Color.White.copy(alpha = 0.18f),
                Color.White.copy(alpha = 0.36f)
            )
        } else {
            listOf(
                Color.White.copy(alpha = 0.30f),
                Color.White.copy(alpha = 0.16f)
            )
        },
        start = Offset.Zero,
        end = Offset(220f, 340f)
    )

    return this
        .clip(shape)
        .background(Brush.verticalGradient(listOf(topFill, bottomFill)))
        .let { mod ->
            if (glassEnabled) {
                mod.drawWithContent {
                    // 顶部斜向液态玻璃柔光反射层
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.20f * clampedAlpha),
                                Color.White.copy(alpha = 0.04f * clampedAlpha),
                                Color.Transparent
                            ),
                            start = Offset.Zero,
                            end = Offset(size.width * 0.85f, size.height * 0.58f)
                        )
                    )
                    drawContent()
                }
            } else {
                mod
            }
        }
        .let { mod ->
            if (!customBorderApplied && (hasWallpaper || glassEnabled)) {
                mod.border(width = 0.9.dp, brush = rimBrush, shape = shape)
            } else {
                mod
            }
        }
}

private val CAMPUS_PREFIX_REGEX = Regex("""^(?:@)?\s*(南湖校区|文昌校区)[\s·-]*""")

/**
 * 优化矿大上课地点在窄列课程卡中的断行排版：
 * 将「南湖校区博2-B402」规范折行为「南湖校区\n博2-B402」，消除「南湖校 / 区 / 博 / 2-B402」碎行现象。
 */
fun formatCourseLocationCompact(rawPosition: String, removeLocationAt: Boolean): String {
    val trimmed = rawPosition.trim().removePrefix("@").trim()
    if (trimmed.isEmpty()) return ""
    val prefix = if (removeLocationAt) "" else "@"
    val match = CAMPUS_PREFIX_REGEX.find(trimmed)
    return if (match != null) {
        val campus = match.groupValues[1]
        val rest = trimmed.substring(match.range.last + 1).trim()
        if (rest.isNotEmpty()) {
            "$prefix$campus\n$rest"
        } else {
            "$prefix$campus"
        }
    } else {
        "$prefix$trimmed"
    }
}
