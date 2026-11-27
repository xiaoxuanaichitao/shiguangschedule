package com.xingheyuzhuan.shiguangschedule.ui.schedule.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xingheyuzhuan.shiguangschedule.data.model.DualColor
import com.xingheyuzhuan.shiguangschedule.data.model.ScheduleGridStyle
import com.xingheyuzhuan.shiguangschedule.data.model.schedule_style.BorderTypeProto
import com.xingheyuzhuan.shiguangschedule.data.model.schedule_style.ScheduleModeProto


/**
 * 【Presentation Layer Model】
 * 将原始 Float/Long 值的 ScheduleGridStyle 转换为 Compose 强类型 Dp/Color 的包装对象。
 */
data class ScheduleGridStyleComposed(
    // Grid 尺寸 (Dp)
    val timeColumnWidth: Dp,
    val dayHeaderHeight: Dp,
    val sectionHeight: Dp,

    // CourseBlock 外观 (Dp & Float)
    val courseBlockCornerRadius: Dp,
    val courseBlockOuterPadding: Dp,
    val courseBlockInnerPadding: Dp,
    val courseBlockAlpha: Float,
    val hasCustomAlpha: Boolean = false,

    // 字体缩放比例
    val fontScale: Float,

    // 颜色 (Color)
    val courseColorMaps: List<DualColor>,

    // UI 渲染开关
    val hideGridLines: Boolean,      // 是否隐藏网格线
    val hideSectionTime: Boolean,
    val hideDateUnderDay: Boolean,
    val showStartTime: Boolean,

    val hideLocation: Boolean,       // 是否隐藏上课地点
    val hideTeacher: Boolean,        // 是否隐藏授课老师
    val removeLocationAt: Boolean,   // 是否移除地点前的 @ 符号

    val textAlignCenterHorizontal: Boolean, // 文字水平居中
    val textAlignCenterVertical: Boolean,   // 文字垂直居中
    val borderType: BorderTypeProto,        // 边框类型 (NONE/SOLID/DASHED)
    val scheduleMode: ScheduleModeProto,    // 供 UI 和逻辑层判断当前走哪种排版规则

    val pageTextColor: Color?, // 页面字符颜色
    val courseTextColor: Color?, // 课程块文字颜色

    // 自定义壁纸联动视觉属性
    val hasWallpaper: Boolean = false,
    val glassEnabled: Boolean = true,
    val cardFrost: Float = 0.28f,
) {
    /** 壁纸模式下若用户从未手动改过卡片不透明度，自动采用 0.76f 通透毛玻璃比例；一旦用户手动拖动滑块则严格遵循用户设定 */
    val effectiveCourseAlpha: Float
        get() = if (hasWallpaper && !hasCustomAlpha) 0.76f else courseBlockAlpha

    companion object {
        /**
         * 扩展函数：将数据模型 (Float/Long) 转换为 UI 强类型模型 (Dp/Color)。
         */
        fun ScheduleGridStyle.toComposedStyle(
            hasWallpaper: Boolean = false,
            glassEnabled: Boolean = true,
            cardFrost: Float = 0.28f
        ): ScheduleGridStyleComposed {
            return ScheduleGridStyleComposed(
                timeColumnWidth = this.timeColumnWidthDp.dp,
                dayHeaderHeight = this.dayHeaderHeightDp.dp,
                sectionHeight = this.sectionHeightDp.dp,
                courseBlockCornerRadius = this.courseBlockCornerRadiusDp.dp,
                courseBlockOuterPadding = this.courseBlockOuterPaddingDp.dp,
                courseBlockInnerPadding = this.courseBlockInnerPaddingDp.dp,
                courseBlockAlpha = this.courseBlockAlphaFloat,
                hasCustomAlpha = this.hasCustomAlpha,
                fontScale = this.courseBlockFontScale,
                courseColorMaps = this.courseColorMaps.map { dual ->
                    DualColor(
                        light = Color(dual.light.toArgb()),
                        dark = Color(dual.dark.toArgb())
                    )
                },
                hideGridLines = this.hideGridLines,
                hideSectionTime = this.hideSectionTime,
                hideDateUnderDay = this.hideDateUnderDay,
                showStartTime = this.showStartTime,
                hideLocation = this.hideLocation,
                hideTeacher = this.hideTeacher,
                removeLocationAt = this.removeLocationAt,
                textAlignCenterHorizontal = this.textAlignCenterHorizontal,
                textAlignCenterVertical = this.textAlignCenterVertical,
                borderType = this.borderType,
                scheduleMode = this.scheduleMode,
                pageTextColor = this.pageTextColorLong?.let { Color(it) },
                courseTextColor = this.courseTextColorLong?.let { Color(it) },
                hasWallpaper = hasWallpaper,
                glassEnabled = glassEnabled,
                cardFrost = cardFrost,
            )
        }
    }
}