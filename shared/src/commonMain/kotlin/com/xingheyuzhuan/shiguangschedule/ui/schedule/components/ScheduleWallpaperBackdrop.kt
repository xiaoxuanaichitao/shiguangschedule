package com.xingheyuzhuan.shiguangschedule.ui.schedule.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
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
    val glassEnabled: Boolean = false,
    val cardFrost: Float = 0.28f
) {
    val hasWallpaper: Boolean
        get() = !wallpaperPath.isNullOrBlank()
}

/**
 * 壁纸模式下的页面文字微阴影（确保白色表头、节次时间在亮色天空/二次元亮底壁纸上依然锐利清晰）。
 */
val WallpaperHeaderTextShadow = Shadow(
    color = Color(0xB8000000),
    offset = Offset(0f, 1.8f),
    blurRadius = 6.0f
)

val WallpaperCardTextShadow = Shadow(
    color = Color(0x5E000000),
    offset = Offset(0f, 1.2f),
    blurRadius = 3.2f
)

/**
 * 全屏沉浸式自定义壁纸背景层：
 * 1. 支持实时高斯模糊（边缘自动微放大消除白边光晕）；
 * 2. 全局护眼柔暗遮罩；
 * 3. 顶栏与左侧时间轴渐进式阅读暗角 + 底部导航栏轻柔托底，彻底解决深/浅复杂壁纸吃字问题。
 */
@Composable
fun ScheduleWallpaperBackdrop(
    visuals: WallpaperVisualConfig,
    modifier: Modifier = Modifier,
    showTopScrim: Boolean = true,
    showBottomScrim: Boolean = true,
    showLeftAxisScrim: Boolean = true
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

        if (showLeftAxisScrim) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(56.dp)
                    .align(Alignment.CenterStart)
                    .background(
                        Brush.horizontalGradient(
                            0.0f to Color.Black.copy(alpha = 0.22f),
                            1.0f to Color.Transparent
                        )
                    )
            )
        }

        if (showTopScrim) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(156.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            0.0f to Color.Black.copy(alpha = 0.44f),
                            0.58f to Color.Black.copy(alpha = 0.20f),
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

    // 关闭液态玻璃时走极轻量化纯色半透 + 单色细描边路径，零额外 Shader 开销，保持满帧丝滑
    if (!glassEnabled) {
        return this
            .clip(shape)
            .background(frostedBase.copy(alpha = clampedAlpha))
            .let { mod ->
                if (!customBorderApplied && hasWallpaper) {
                    mod.border(width = 0.75.dp, color = Color.White.copy(alpha = 0.26f), shape = shape)
                } else {
                    mod
                }
            }
    }

    val topFill = lerp(frostedBase, Color.White, 0.10f).copy(alpha = clampedAlpha)
    val bottomFill = frostedBase.copy(alpha = (clampedAlpha * 0.88f).coerceIn(0.12f, 1.0f))

    val rimBrush = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.62f),
            Color.White.copy(alpha = 0.18f),
            Color.White.copy(alpha = 0.36f)
        ),
        start = Offset.Zero,
        end = Offset(220f, 340f)
    )

    return this
        .clip(shape)
        .background(Brush.verticalGradient(listOf(topFill, bottomFill)))
        .drawWithContent {
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
        .let { mod ->
            if (!customBorderApplied) {
                mod.border(width = 0.9.dp, brush = rimBrush, shape = shape)
            } else {
                mod
            }
        }
}

private val CAMPUS_PREFIX_REGEX = Regex("""^(?:@)?\s*(南湖校区|文昌校区)[\s·-]*""")
private val CJK_BEFORE_ALNUM_REGEX = Regex("""([\u4E00-\u9FFF])(?=[0-9A-Za-z])""")

/**
 * 优化矿大上课地点在窄列课程卡中的断行排版：
 * 1. 将「南湖校区博2-B402」规范折行为「南湖校区\n博2-B402」；
 * 2. 在中文教学楼简称与楼号之间插入 Unicode Word Joiner (\u2060)，防止 Android 按中英边界把「博」单独折成孤立单字行。
 */
fun formatCourseLocationCompact(rawPosition: String, removeLocationAt: Boolean): String {
    val trimmed = rawPosition.trim().removePrefix("@").trim()
    if (trimmed.isEmpty()) return ""
    val prefix = if (removeLocationAt) "" else "@"
    val match = CAMPUS_PREFIX_REGEX.find(trimmed)
    return if (match != null) {
        val campus = match.groupValues[1]
        val rest = trimmed.substring(match.range.last + 1).trim()
            .replace(CJK_BEFORE_ALNUM_REGEX, "$1\u2060")
        if (rest.isNotEmpty()) {
            "$prefix$campus\n$rest"
        } else {
            "$prefix$campus"
        }
    } else {
        "$prefix${trimmed.replace(CJK_BEFORE_ALNUM_REGEX, "$1\u2060")}"
    }
}
