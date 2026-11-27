package com.xingheyuzhuan.shiguangschedule.ui.today

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xingheyuzhuan.shiguangschedule.Destination
import com.xingheyuzhuan.shiguangschedule.cumt.CumtExamItem
import com.xingheyuzhuan.shiguangschedule.data.model.ScheduleGridStyle
import com.xingheyuzhuan.shiguangschedule.ui.cumt.CumtAccent
import com.xingheyuzhuan.shiguangschedule.ui.cumt.CumtStatusPill
import com.xingheyuzhuan.shiguangschedule.ui.cumt.CumtSurfaceCard
import com.xingheyuzhuan.shiguangschedule.ui.schedule.components.ScheduleWallpaperBackdrop
import com.xingheyuzhuan.shiguangschedule.ui.schedule.components.WallpaperCardTextShadow
import com.xingheyuzhuan.shiguangschedule.ui.schedule.components.WallpaperHeaderTextShadow
import com.xingheyuzhuan.shiguangschedule.ui.schedule.components.WallpaperVisualConfig
import com.xingheyuzhuan.shiguangschedule.ui.schedule.components.scheduleGlassCardSurface
import com.xingheyuzhuan.shiguangschedule.ui.theme.LocalIsDarkTheme
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import org.koin.compose.viewmodel.koinViewModel
import shiguangschedule.shared.generated.resources.Res
import shiguangschedule.shared.generated.resources.chevron_right_24px
import shiguangschedule.shared.generated.resources.course_position_prefix
import shiguangschedule.shared.generated.resources.course_teacher_prefix
import shiguangschedule.shared.generated.resources.date_format_year_month_day
import shiguangschedule.shared.generated.resources.label_credit
import shiguangschedule.shared.generated.resources.label_remark
import shiguangschedule.shared.generated.resources.schedule_24px
import shiguangschedule.shared.generated.resources.status_semester_ended
import shiguangschedule.shared.generated.resources.text_no_courses_today
import shiguangschedule.shared.generated.resources.title_current_week
import shiguangschedule.shared.generated.resources.title_semester_not_set
import shiguangschedule.shared.generated.resources.title_today_schedule
import shiguangschedule.shared.generated.resources.title_vacation_until_start
import shiguangschedule.shared.generated.resources.week_days_full_names
import kotlin.time.Clock

private const val DEFAULT_TIME_ZERO = "00:00"
private const val EMPTY_TIME_PLACEHOLDER = "--:--"
private const val DEFAULT_DAYS_ZERO = "0"
private const val DEFAULT_OVERDUE_DAYS = 1

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScheduleScreen(
    onNavigate: (Destination) -> Unit,
    onBack: () -> Unit,
    hasSharedWallpaper: Boolean = false,
    viewModel: TodayScheduleViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val gridStyle by viewModel.gridStyle.collectAsState()
    val wallpaperVisuals by viewModel.wallpaperVisuals.collectAsState()
    val upcomingExamHint by viewModel.upcomingExamHint.collectAsState()
    val isDark = LocalIsDarkTheme.current

    LaunchedEffect(isDark) {
        viewModel.updateDarkTheme(isDark)
    }

    val hasWallpaper = hasSharedWallpaper || wallpaperVisuals.hasWallpaper
    val effectiveVisuals = if (hasSharedWallpaper && !wallpaperVisuals.hasWallpaper) {
        wallpaperVisuals.copy(wallpaperPath = "__shared__")
    } else {
        wallpaperVisuals
    }
    val customPageColor = gridStyle.pageTextColorLong?.let { Color(it) }
    val headerTitleColor = customPageColor ?: if (hasWallpaper) Color.White else MaterialTheme.colorScheme.onSurface
    val headerShadow = if (hasWallpaper && customPageColor == null) WallpaperHeaderTextShadow else null

    val fallbackBgModifier = if (!hasWallpaper) {
        if (isDark) {
            Modifier.background(MaterialTheme.colorScheme.background)
        } else {
            Modifier.background(
                Brush.verticalGradient(listOf(Color(0xFFF2FBF8), Color(0xFFF8FAFC)))
            )
        }
    } else {
        Modifier
    }

    Box(modifier = Modifier.fillMaxSize().then(fallbackBgModifier)) {
        if (!hasSharedWallpaper && wallpaperVisuals.hasWallpaper) {
            ScheduleWallpaperBackdrop(visuals = wallpaperVisuals)
        }

        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = stringResource(Res.string.title_today_schedule),
                            style = MaterialTheme.typography.titleLarge.copy(shadow = headerShadow),
                            fontWeight = FontWeight.ExtraBold,
                            color = headerTitleColor
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent
                    )
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = innerPadding.calculateTopPadding())
            ) {
                when (val state = uiState) {
                    is TodayUiState.Loading -> { /* 可放置圆圈加载 */ }
                    is TodayUiState.Success -> {
                        TodayContent(
                            state = state,
                            gridStyle = gridStyle,
                            wallpaperVisuals = effectiveVisuals,
                            isDark = isDark,
                            upcomingExamHint = upcomingExamHint,
                            onExamHintClick = { onNavigate(Destination.CumtExams) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TodayContent(
    state: TodayUiState.Success,
    gridStyle: ScheduleGridStyle,
    wallpaperVisuals: WallpaperVisualConfig = WallpaperVisualConfig(),
    isDark: Boolean,
    modifier: Modifier = Modifier,
    upcomingExamHint: CumtExamItem? = null,
    onExamHintClick: () -> Unit = {}
) {
    val currentTime = remember { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).time }

    val targetScrollIndex = remember(state.courses, currentTime) {
        val firstActiveIndex = state.courses.indexOfFirst { model ->
            try {
                LocalTime.parse(model.endTime ?: DEFAULT_TIME_ZERO) >= currentTime
            } catch (e: Exception) {
                true
            }
        }

        if (firstActiveIndex == -1) {
            (state.courses.size - 1).coerceAtLeast(0)
        } else {
            firstActiveIndex
        }
    }

    val scrollState = rememberLazyListState(
        initialFirstVisibleItemIndex = targetScrollIndex
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        val weekDays = stringArrayResource(Res.array.week_days_full_names)

        val year = state.today.year.toString()
        val month = state.today.month.number.toString()
        val day = state.today.day.toString()

        val formattedDate = stringResource(Res.string.date_format_year_month_day, year, month, day)

        val dateStr = remember(state.today, weekDays, formattedDate) {
            val weekDayName = weekDays.getOrNull(state.today.dayOfWeek.isoDayNumber - 1).orEmpty()
            "$formattedDate $weekDayName"
        }

        val subTitle = when (state.status) {
            TodayStatus.NoSemesterConfig -> stringResource(Res.string.title_semester_not_set)

            TodayStatus.Vacation -> {
                val days = if (state.startDate != null) {
                    state.today.daysUntil(state.startDate).toString()
                } else DEFAULT_DAYS_ZERO
                stringResource(Res.string.title_vacation_until_start, days)
            }

            TodayStatus.SemesterEnded -> {
                val overdueDays = if (state.startDate != null) {
                    val targetDayOfWeek = DayOfWeek(state.firstDayOfWeek)
                    val daysShift = (state.startDate.dayOfWeek.ordinal - targetDayOfWeek.ordinal + 7) % 7
                    val firstWeekStart = LocalDate.fromEpochDays(state.startDate.toEpochDays() - daysShift)

                    val semesterEndDate = LocalDate.fromEpochDays(
                        firstWeekStart.toEpochDays() + (state.totalWeeks * 7) - 1
                    )
                    semesterEndDate.daysUntil(state.today).coerceAtLeast(DEFAULT_OVERDUE_DAYS)
                } else {
                    DEFAULT_OVERDUE_DAYS
                }
                stringResource(Res.string.status_semester_ended, overdueDays)
            }

            TodayStatus.Normal -> stringResource(Res.string.title_current_week, state.weekIndex.toString())
        }

        val hasWallpaper = wallpaperVisuals.hasWallpaper
        val customPageColor = gridStyle.pageTextColorLong?.let { Color(it) }
        val pagePrimaryText = customPageColor ?: if (hasWallpaper) Color.White else MaterialTheme.colorScheme.onSurface
        val pageSecondaryText = if (hasWallpaper && customPageColor == null) {
            Color.White.copy(alpha = 0.84f)
        } else {
            customPageColor?.copy(alpha = 0.75f) ?: MaterialTheme.colorScheme.outline
        }
        val headerShadow = if (hasWallpaper && customPageColor == null) WallpaperHeaderTextShadow else null

        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Text(
                text = dateStr,
                style = MaterialTheme.typography.titleMedium.copy(shadow = headerShadow),
                fontWeight = FontWeight.Bold,
                color = pagePrimaryText
            )
            Text(
                text = subTitle,
                style = MaterialTheme.typography.labelMedium.copy(shadow = headerShadow),
                color = pageSecondaryText
            )
        }

        if (upcomingExamHint != null) {
            val examDate = runCatching {
                LocalDate(upcomingExamHint.year, upcomingExamHint.month, upcomingExamHint.day)
            }.getOrNull()
            val daysLeft = examDate?.let { state.today.daysUntil(it) }
            if (daysLeft != null && daysLeft in 0..7) {
                CumtSurfaceCard(
                    onClick = onExamHintClick,
                    cornerRadius = 16.dp,
                    containerColor = if (hasWallpaper) {
                        Color(0xFF8C2D25).copy(alpha = 0.68f)
                    } else {
                        CumtAccent.CORAL.containerColor().copy(alpha = 0.72f)
                    },
                    borderColor = if (hasWallpaper) {
                        Color.White.copy(alpha = 0.35f)
                    } else {
                        CumtAccent.CORAL.contentColor().copy(alpha = 0.25f)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    val examFg = if (hasWallpaper) Color.White else CumtAccent.CORAL.contentColor()
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CumtStatusPill(
                            text = if (daysLeft == 0) "今天考试" else "剩 $daysLeft 天",
                            containerColor = if (hasWallpaper) Color.White.copy(alpha = 0.22f) else CumtAccent.CORAL.contentColor(),
                            contentColor = if (hasWallpaper) Color.White else MaterialTheme.colorScheme.surface,
                            icon = Res.drawable.schedule_24px
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = upcomingExamHint.courseName,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = examFg,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val detail = listOf(upcomingExamHint.dateTimeRaw, upcomingExamHint.location)
                                .filter { it.isNotBlank() }
                                .joinToString(" · ")
                            if (detail.isNotBlank()) {
                                Text(
                                    text = detail,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = examFg.copy(alpha = 0.88f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Icon(
                            imageVector = vectorResource(Res.drawable.chevron_right_24px),
                            contentDescription = null,
                            tint = examFg,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (state.courses.isEmpty()) {
            EmptyStateView(hasWallpaper = hasWallpaper, textColor = pageSecondaryText)
        } else {
            LazyColumn(
                state = scrollState,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(
                    bottom = 88.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                itemsIndexed(state.courses) { _, model ->
                    CourseTimelineItem(
                        model = model,
                        gridStyle = gridStyle,
                        wallpaperVisuals = wallpaperVisuals,
                        isDark = isDark
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
fun CourseTimelineItem(
    model: CourseDisplayModel,
    gridStyle: ScheduleGridStyle,
    wallpaperVisuals: WallpaperVisualConfig = WallpaperVisualConfig(),
    isDark: Boolean
) {
    val currentTime = remember { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).time }
    val isFinished = remember(model.endTime, currentTime) {
        try {
            LocalTime.parse(model.endTime ?: DEFAULT_TIME_ZERO) < currentTime
        } catch (e: Exception) { false }
    }

    val hasWallpaper = wallpaperVisuals.hasWallpaper
    val colorPair = gridStyle.courseColorMaps.getOrElse(model.course.colorInt) {
        ScheduleGridStyle.DEFAULT_COLOR_MAPS[0]
    }
    val themeColor = if (isDark && !hasWallpaper) colorPair.dark else colorPair.light
    val effectiveAlpha = if (hasWallpaper && !gridStyle.hasCustomAlpha) {
        0.82f
    } else {
        gridStyle.courseBlockAlphaFloat.coerceAtLeast(if (hasWallpaper) 0.55f else 0.15f)
    }
    val cardCorner = gridStyle.courseBlockCornerRadiusDp.coerceAtLeast(14f).dp

    val customCourseTextColor = gridStyle.courseTextColorLong?.let { Color(it) }
    val cardTextColor = customCourseTextColor ?: when {
        hasWallpaper -> Color.White
        themeColor.luminance() > 0.72f -> Color(0xFF1C242B)
        else -> Color.White
    }
    val cardSubTextColor = cardTextColor.copy(alpha = 0.90f)
    val cardTextShadow = if (hasWallpaper && customCourseTextColor == null) WallpaperCardTextShadow else null

    val customPageColor = gridStyle.pageTextColorLong?.let { Color(it) }
    val timePrimaryColor = customPageColor ?: if (hasWallpaper) Color.White else MaterialTheme.colorScheme.onSurface
    val timeSecondaryColor = if (hasWallpaper && customPageColor == null) {
        Color.White.copy(alpha = 0.84f)
    } else {
        customPageColor?.copy(alpha = 0.70f) ?: MaterialTheme.colorScheme.outline
    }
    val timeShadow = if (hasWallpaper && customPageColor == null) WallpaperHeaderTextShadow else null

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer(
                alpha = when {
                    !isFinished -> 1f
                    hasWallpaper -> 0.86f
                    else -> 0.56f
                }
            )
    ) {
        Column(
            modifier = Modifier.width(64.dp).padding(top = 4.dp),
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = model.startTime ?: EMPTY_TIME_PLACEHOLDER,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 17.sp,
                    textDecoration = if (isFinished) TextDecoration.LineThrough else null,
                    shadow = timeShadow
                ),
                fontWeight = FontWeight.Bold,
                color = timePrimaryColor
            )
            Text(
                text = model.endTime ?: EMPTY_TIME_PLACEHOLDER,
                style = MaterialTheme.typography.labelSmall.copy(shadow = timeShadow),
                color = timeSecondaryColor
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .scheduleGlassCardSurface(
                        baseColor = if (isFinished && hasWallpaper) {
                            androidx.compose.ui.graphics.lerp(themeColor, Color(0xFF2B3442), 0.35f)
                        } else {
                            themeColor
                        },
                        alpha = effectiveAlpha,
                        cornerRadius = cardCorner,
                        hasWallpaper = hasWallpaper,
                        glassEnabled = wallpaperVisuals.glassEnabled && (hasWallpaper || effectiveAlpha < 0.96f),
                        cardFrost = wallpaperVisuals.cardFrost
                    )
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = model.course.name,
                            style = MaterialTheme.typography.titleMedium.copy(
                                textDecoration = if (isFinished) TextDecoration.LineThrough else null,
                                shadow = cardTextShadow
                            ),
                            fontWeight = FontWeight.Bold,
                            color = cardTextColor,
                            modifier = Modifier.weight(1f)
                        )
                        if (isFinished) {
                            CumtStatusPill(
                                text = "已结束",
                                containerColor = Color.Black.copy(alpha = 0.22f),
                                contentColor = cardTextColor.copy(alpha = 0.92f)
                            )
                        }
                    }

                    if (model.course.position.isNotBlank()) {
                        Text(
                            text = stringResource(Res.string.course_position_prefix, model.course.position),
                            style = MaterialTheme.typography.bodySmall.copy(shadow = cardTextShadow),
                            color = cardSubTextColor,
                            modifier = Modifier.padding(top = 3.dp)
                        )
                    }

                    if (model.course.teacher.isNotBlank()) {
                        Text(
                            text = stringResource(Res.string.course_teacher_prefix, model.course.teacher),
                            style = MaterialTheme.typography.bodySmall.copy(shadow = cardTextShadow),
                            color = cardSubTextColor
                        )
                    }

                    val credit = model.course.credit
                    if (credit != null && credit > 0f) {
                        val creditValStr = if (credit % 1f == 0f) {
                            credit.toInt().toString()
                        } else {
                            credit.toString()
                        }
                        val creditLabel = stringResource(Res.string.label_credit)
                        Text(
                            text = "$creditLabel: $creditValStr",
                            style = MaterialTheme.typography.bodySmall.copy(shadow = cardTextShadow),
                            color = cardSubTextColor
                        )
                    }
                }
            }
            model.course.remark?.takeIf { it.isNotBlank() }?.let { remark ->
                val remarkShape = RoundedCornerShape(12.dp)
                Box(
                    modifier = Modifier
                        .padding(top = 6.dp, start = 2.dp)
                        .fillMaxWidth()
                        .clip(remarkShape)
                        .background(
                            color = if (hasWallpaper) {
                                Color.Black.copy(alpha = 0.28f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            }
                        )
                        .let { mod ->
                            if (hasWallpaper) {
                                mod.border(0.8.dp, Color.White.copy(alpha = 0.24f), remarkShape)
                            } else {
                                mod
                            }
                        }
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Column {
                        Text(
                            text = stringResource(Res.string.label_remark),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (hasWallpaper) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.outline,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = remark,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (hasWallpaper) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyStateView(
    hasWallpaper: Boolean = false,
    textColor: Color = Color.Unspecified
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (hasWallpaper) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.Black.copy(alpha = 0.28f),
                border = BorderStroke(0.9.dp, Color.White.copy(alpha = 0.34f))
            ) {
                Text(
                    text = stringResource(Res.string.text_no_courses_today),
                    style = TextStyle(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        shadow = WallpaperHeaderTextShadow
                    ),
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp)
                )
            }
        } else {
            Text(
                text = stringResource(Res.string.text_no_courses_today),
                style = MaterialTheme.typography.bodyLarge,
                color = if (textColor != Color.Unspecified) textColor else MaterialTheme.colorScheme.outline
            )
        }
    }
}