package com.xingheyuzhuan.shiguangschedule.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xingheyuzhuan.shiguangschedule.Destination
import com.xingheyuzhuan.shiguangschedule.data.model.AppThemeMode
import com.xingheyuzhuan.shiguangschedule.ui.components.NativeNumberPicker
import com.xingheyuzhuan.shiguangschedule.ui.cumt.CumtSectionHeader
import com.xingheyuzhuan.shiguangschedule.ui.cumt.CumtSegmentedBar
import com.xingheyuzhuan.shiguangschedule.ui.cumt.CumtSurfaceCard
import com.xingheyuzhuan.shiguangschedule.ui.schedule.components.WallpaperHeaderTextShadow
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.number
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import org.koin.compose.viewmodel.koinViewModel
import shiguangschedule.shared.generated.resources.Res
import shiguangschedule.shared.generated.resources.action_cancel
import shiguangschedule.shared.generated.resources.action_confirm
import shiguangschedule.shared.generated.resources.chevron_right_24px
import shiguangschedule.shared.generated.resources.date_format_year_month_day
import shiguangschedule.shared.generated.resources.day_of_week_monday
import shiguangschedule.shared.generated.resources.day_of_week_sunday
import shiguangschedule.shared.generated.resources.desc_course_conversion
import shiguangschedule.shared.generated.resources.desc_course_management
import shiguangschedule.shared.generated.resources.desc_current_week_manual
import shiguangschedule.shared.generated.resources.desc_first_day_of_week
import shiguangschedule.shared.generated.resources.desc_manage_course_tables
import shiguangschedule.shared.generated.resources.desc_more_options
import shiguangschedule.shared.generated.resources.desc_notification_settings
import shiguangschedule.shared.generated.resources.desc_personalization
import shiguangschedule.shared.generated.resources.desc_quick_actions
import shiguangschedule.shared.generated.resources.desc_schedule_management
import shiguangschedule.shared.generated.resources.desc_set_start_date
import shiguangschedule.shared.generated.resources.desc_show_non_current_week
import shiguangschedule.shared.generated.resources.desc_show_weekends
import shiguangschedule.shared.generated.resources.desc_total_weeks
import shiguangschedule.shared.generated.resources.dialog_title_manual_set_week
import shiguangschedule.shared.generated.resources.dialog_title_select_total_weeks
import shiguangschedule.shared.generated.resources.dialog_title_set_first_day_of_week
import shiguangschedule.shared.generated.resources.holiday_management_desc
import shiguangschedule.shared.generated.resources.holiday_management_title
import shiguangschedule.shared.generated.resources.item_course_conversion
import shiguangschedule.shared.generated.resources.item_course_management
import shiguangschedule.shared.generated.resources.item_current_week
import shiguangschedule.shared.generated.resources.item_first_day_of_week
import shiguangschedule.shared.generated.resources.item_more_options
import shiguangschedule.shared.generated.resources.item_personalization
import shiguangschedule.shared.generated.resources.item_quick_actions
import shiguangschedule.shared.generated.resources.item_set_start_date
import shiguangschedule.shared.generated.resources.item_show_non_current_week
import shiguangschedule.shared.generated.resources.item_show_weekends
import shiguangschedule.shared.generated.resources.item_total_weeks
import shiguangschedule.shared.generated.resources.more_horiz_24px
import shiguangschedule.shared.generated.resources.section_title_advanced_features
import shiguangschedule.shared.generated.resources.section_title_general_settings
import shiguangschedule.shared.generated.resources.status_current_week_format
import shiguangschedule.shared.generated.resources.status_not_set
import shiguangschedule.shared.generated.resources.status_set_start_date_first
import shiguangschedule.shared.generated.resources.status_total_weeks_format
import shiguangschedule.shared.generated.resources.title_course_notification_settings
import shiguangschedule.shared.generated.resources.title_manage_course_tables
import shiguangschedule.shared.generated.resources.title_schedule_management
import shiguangschedule.shared.generated.resources.title_schedule_settings
import shiguangschedule.shared.generated.resources.title_vacation

private val SETTING_PADDING = 16.dp
private val SECTION_SPACING = 16.dp
private val ITEM_SPACING = 16.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigate: (Destination) -> Unit,
    onBack: () -> Unit,
    hasSharedWallpaper: Boolean = false,
    viewModel: SettingsViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val cardBgColor = if (hasSharedWallpaper) {
        MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.82f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    val cardBorderColor = if (hasSharedWallpaper) {
        Color.White.copy(alpha = 0.34f)
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = if (hasSharedWallpaper) Color.Transparent else MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.title_schedule_settings),
                        color = if (hasSharedWallpaper) Color.White else MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (hasSharedWallpaper) FontWeight.ExtraBold else FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleLarge.copy(
                            shadow = if (hasSharedWallpaper) WallpaperHeaderTextShadow else null
                        )
                    )
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (hasSharedWallpaper) Color.Transparent else MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = if (hasSharedWallpaper) Color.Black.copy(alpha = 0.24f) else MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        if (!uiState.isReady) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding))
        } else {
            val appSettings = uiState.appSettings
            val courseTableConfig = uiState.courseConfig
            val displayCurrentWeek = uiState.currentWeek

            val showWeekends = courseTableConfig?.showWeekends ?: true
            val semesterStartDateString = courseTableConfig?.semesterStartDate
            val semesterTotalWeeks = courseTableConfig?.semesterTotalWeeks ?: 20
            val firstDayOfWeekInt = courseTableConfig?.firstDayOfWeek ?: DayOfWeek.MONDAY.isoDayNumber

            val semesterStartDate: LocalDate? = remember(semesterStartDateString) {
                semesterStartDateString?.let {
                    try {
                        LocalDate.parse(it)
                    } catch (e: Exception) {
                        null
                    }
                }
            }

            var showTotalWeeksDialog by remember { mutableStateOf(false) }
            var showManualWeekDialog by remember { mutableStateOf(false) }
            var showDatePickerModal by remember { mutableStateOf(false) }
            var showFirstDayOfWeekDialog by remember { mutableStateOf(false) }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.TopCenter
            ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 760.dp)
                    .padding(horizontal = SETTING_PADDING),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(SECTION_SPACING),
                contentPadding = PaddingValues(
                    bottom = 88.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp
                )
            ) {
                item {
                    AppearanceQuickSection(
                        selectedThemeMode = appSettings.themeMode,
                        onThemeModeSelected = viewModel::onThemeModeChanged,
                        onThemeSettingsClick = { onNavigate(Destination.ThemeSettings) },
                        cardBgColor = cardBgColor,
                        cardBorderColor = cardBorderColor
                    )
                }
                item {
                    GeneralSettingsSection(
                        showNonCurrentWeek = appSettings.showNonCurrentWeekCourses,
                        onShowNonCurrentWeekChanged = { isChecked -> viewModel.onShowNonCurrentWeekChanged(isChecked) },
                        showWeekends = showWeekends,
                        onShowWeekendsChanged = { isChecked -> viewModel.onShowWeekendsChanged(isChecked) },
                        semesterStartDate = semesterStartDate,
                        semesterTotalWeeks = semesterTotalWeeks,
                        firstDayOfWeekInt = firstDayOfWeekInt,
                        displayCurrentWeek = displayCurrentWeek,
                        onSemesterStartDateClick = { showDatePickerModal = true },
                        onSemesterTotalWeeksClick = { showTotalWeeksDialog = true },
                        onManualWeekClick = { showManualWeekDialog = true },
                        onFirstDayOfWeekClick = { showFirstDayOfWeekDialog = true },
                        onQuickActionsClick = { onNavigate(Destination.QuickActions) },
                        cardBgColor = cardBgColor,
                        cardBorderColor = cardBorderColor
                    )
                }
                item {
                    AdvancedSettingsSection(
                        onNavigate = onNavigate,
                        cardBgColor = cardBgColor,
                        cardBorderColor = cardBorderColor
                    )
                }
            }
            }

            if (showDatePickerModal) {
                val datePickerState = rememberDatePickerState(
                    initialSelectedDateMillis = remember(semesterStartDate) {
                        semesterStartDate?.let {
                            LocalDate(it.year, it.month.number, it.day)
                                .atStartOfDayIn(TimeZone.UTC)
                                .toEpochMilliseconds()
                        }
                    }
                )

                DatePickerDialog(
                    onDismissRequest = { showDatePickerModal = false },
                    confirmButton = {
                        TextButton(onClick = {
                            datePickerState.selectedDateMillis?.let { viewModel.onSemesterStartDateSelected(it) }
                            showDatePickerModal = false
                        }) {
                            Text(stringResource(Res.string.action_confirm))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDatePickerModal = false }) {
                            Text(stringResource(Res.string.action_cancel))
                        }
                    }
                ) {
                    DatePicker(
                        state = datePickerState,
                        showModeToggle = false
                    )
                }
            }

            if (showTotalWeeksDialog) {
                NumberPickerDialog(
                    title = stringResource(Res.string.dialog_title_select_total_weeks),
                    range = 1..30,
                    initialValue = semesterTotalWeeks,
                    onDismiss = { showTotalWeeksDialog = false },
                    onConfirm = { selectedWeeks ->
                        viewModel.onSemesterTotalWeeksSelected(selectedWeeks)
                        showTotalWeeksDialog = false
                    }
                )
            }

            if (showManualWeekDialog) {
                ManualWeekPickerDialog(
                    totalWeeks = semesterTotalWeeks,
                    currentWeek = displayCurrentWeek,
                    onDismiss = { showManualWeekDialog = false },
                    onConfirm = { weekNumber ->
                        viewModel.onCurrentWeekManuallySet(weekNumber)
                        showManualWeekDialog = false
                    }
                )
            }

            if (showFirstDayOfWeekDialog) {
                DayOfWeekPickerDialog(
                    initialDayOfWeekInt = firstDayOfWeekInt,
                    onDismiss = { showFirstDayOfWeekDialog = false },
                    onConfirm = { selectedDayInt ->
                        viewModel.onFirstDayOfWeekSelected(selectedDayInt)
                        showFirstDayOfWeekDialog = false
                    }
                )
            }
        }
    }
}

/**
 * 外观与深浅色快速切换卡片
 */
@Composable
private fun AppearanceQuickSection(
    selectedThemeMode: AppThemeMode,
    onThemeModeSelected: (AppThemeMode) -> Unit,
    onThemeSettingsClick: () -> Unit,
    cardBgColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    cardBorderColor: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
) {
    CumtSurfaceCard(
        containerColor = cardBgColor,
        borderColor = cardBorderColor,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(SETTING_PADDING),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CumtSectionHeader(
                title = "外观与界面字号"
            )

            CumtSegmentedBar(
                options = listOf("跟随系统", "浅色", "深色"),
                selectedIndex = AppThemeMode.entries.indexOf(selectedThemeMode).coerceAtLeast(0),
                onSelect = { index -> onThemeModeSelected(AppThemeMode.entries[index]) },
                modifier = Modifier.fillMaxWidth()
            )

            SettingItem(
                title = "主题配色与软件字号",
                subtitle = "调节软件全局字号（不影响课表）、动态取色或自定义主题色",
                onClick = onThemeSettingsClick
            )
        }
    }
}

/**
 * 通用设置卡片
 */
@Composable
private fun GeneralSettingsSection(
    showNonCurrentWeek: Boolean,
    onShowNonCurrentWeekChanged: (Boolean) -> Unit,
    showWeekends: Boolean,
    onShowWeekendsChanged: (Boolean) -> Unit,
    semesterStartDate: LocalDate?,
    semesterTotalWeeks: Int,
    firstDayOfWeekInt: Int,
    displayCurrentWeek: Int?,
    onSemesterStartDateClick: () -> Unit,
    onSemesterTotalWeeksClick: () -> Unit,
    onManualWeekClick: () -> Unit,
    onFirstDayOfWeekClick: () -> Unit,
    onQuickActionsClick: () -> Unit,
    cardBgColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    cardBorderColor: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
) {
    CumtSurfaceCard(
        containerColor = cardBgColor,
        borderColor = cardBorderColor,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(SETTING_PADDING),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CumtSectionHeader(
                title = stringResource(Res.string.section_title_general_settings)
            )

            SettingItem(
                title = stringResource(Res.string.item_show_non_current_week),
                subtitle = stringResource(Res.string.desc_show_non_current_week)
            ) {
                Switch(checked = showNonCurrentWeek, onCheckedChange = onShowNonCurrentWeekChanged)
            }

            SettingItem(
                title = stringResource(Res.string.item_show_weekends),
                subtitle = stringResource(Res.string.desc_show_weekends)
            ) {
                Switch(checked = showWeekends, onCheckedChange = onShowWeekendsChanged)
            }

            SettingItem(
                title = stringResource(Res.string.item_set_start_date),
                subtitle = stringResource(Res.string.desc_set_start_date),
                onClick = onSemesterStartDateClick
            ) {
                val formattedDate = semesterStartDate?.let {
                    stringResource(
                        Res.string.date_format_year_month_day,
                        it.year.toString(),
                        it.month.number.toString(),
                        it.day.toString()
                    )
                } ?: stringResource(Res.string.status_not_set)

                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            SettingItem(
                title = stringResource(Res.string.item_total_weeks),
                subtitle = stringResource(Res.string.desc_total_weeks),
                onClick = onSemesterTotalWeeksClick
            ) {
                Text(
                    text = stringResource(Res.string.status_total_weeks_format, semesterTotalWeeks),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            SettingItem(
                title = stringResource(Res.string.item_current_week),
                subtitle = stringResource(Res.string.desc_current_week_manual),
                onClick = onManualWeekClick
            ) {
                val weekStatusText = when {
                    semesterStartDate == null -> stringResource(Res.string.status_set_start_date_first)
                    displayCurrentWeek == null -> stringResource(Res.string.title_vacation)
                    else -> stringResource(Res.string.status_current_week_format, displayCurrentWeek)
                }
                Text(
                    text = weekStatusText,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            SettingItem(
                title = stringResource(Res.string.item_first_day_of_week),
                subtitle = stringResource(Res.string.desc_first_day_of_week),
                onClick = onFirstDayOfWeekClick
            ) {
                val dayText = when (firstDayOfWeekInt) {
                    DayOfWeek.MONDAY.isoDayNumber -> stringResource(Res.string.day_of_week_monday)
                    DayOfWeek.SUNDAY.isoDayNumber -> stringResource(Res.string.day_of_week_sunday)
                    else -> stringResource(Res.string.day_of_week_monday)
                }
                Text(
                    text = dayText,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            SettingItem(
                title = stringResource(Res.string.item_quick_actions),
                subtitle = stringResource(Res.string.desc_quick_actions),
                onClick = onQuickActionsClick
            )
        }
    }
}

/**
 * 高级功能卡片
 */
@Composable
private fun AdvancedSettingsSection(
    onNavigate: (Destination) -> Unit,
    cardBgColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    cardBorderColor: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
) {
    CumtSurfaceCard(
        containerColor = cardBgColor,
        borderColor = cardBorderColor,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(SETTING_PADDING),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CumtSectionHeader(
                title = stringResource(Res.string.section_title_advanced_features)
            )
            SettingItem(
                title = "矿大统一身份认证",
                subtitle = "绑定学号以直连校园卡、图书馆与教务自动填充",
                onClick = { onNavigate(Destination.CumtAuth) }
            )
            SettingItem(
                title = "矿大 AI 助手与悬浮窗",
                subtitle = "配置模型 Key、调节全局悬浮球图标大小与 MCP 服务",
                onClick = { onNavigate(Destination.CumtAiCourseImport) }
            )
            SettingItem(
                title = stringResource(Res.string.item_course_conversion),
                subtitle = stringResource(Res.string.desc_course_conversion),
                onClick = { onNavigate(Destination.CourseTableConversion) }
            )
            SettingItem(
                title = stringResource(Res.string.title_course_notification_settings),
                subtitle = stringResource(Res.string.desc_notification_settings),
                onClick = { onNavigate(Destination.NotificationSettings) }
            )
            SettingItem(
                title = stringResource(Res.string.title_manage_course_tables),
                subtitle = stringResource(Res.string.desc_manage_course_tables),
                onClick = { onNavigate(Destination.ManageCourseTables) }
            )
            SettingItem(
                title = stringResource(Res.string.item_course_management),
                subtitle = stringResource(Res.string.desc_course_management),
                onClick = { onNavigate(Destination.CourseManagementList) }
            )
            SettingItem(
                title = stringResource(Res.string.title_schedule_management),
                subtitle = stringResource(Res.string.desc_schedule_management),
                onClick = { onNavigate(Destination.TimeScheduleManagement) }
            )
            SettingItem(
                title = stringResource(Res.string.item_personalization),
                subtitle = stringResource(Res.string.desc_personalization),
                onClick = { onNavigate(Destination.StyleSettings) }
            )
            SettingItem(
                title = stringResource(Res.string.holiday_management_title),
                subtitle = stringResource(Res.string.holiday_management_desc),
                onClick = { onNavigate(Destination.HolidayManagement) }
            )
            SettingItem(
                title = stringResource(Res.string.item_more_options),
                subtitle = stringResource(Res.string.desc_more_options),
                onClick = { onNavigate(Destination.MoreOptions) },
                icon = vectorResource(Res.drawable.more_horiz_24px)
            )
        }
    }
}

/**
 * 封装单个设置项的可组合函数，提高代码复用性
 */
@Composable
private fun SettingItem(
    title: String,
    subtitle: String,
    icon: ImageVector = vectorResource(Res.drawable.chevron_right_24px),
    onClick: (() -> Unit)? = null,
    trailingContent: @Composable () -> Unit = {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.weight(1f).padding(end = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        trailingContent()
    }
}

/**
 * 手动周数选择器对话框
 */
@Composable
fun ManualWeekPickerDialog(
    totalWeeks: Int,
    currentWeek: Int?,
    onDismiss: () -> Unit,
    onConfirm: (Int?) -> Unit
) {
    val optionOnVacationText = stringResource(Res.string.title_vacation)

    val weekOptions = listOf(optionOnVacationText) + (1..totalWeeks).map {
        stringResource(Res.string.status_current_week_format, it)
    }

    val initialSelectedValue = when (currentWeek) {
        null -> optionOnVacationText
        else -> stringResource(Res.string.status_current_week_format, currentWeek)
    }

    var dialogSelectedValue by remember { mutableStateOf(initialSelectedValue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.dialog_title_manual_set_week)) },
        text = {
            NativeNumberPicker(
                values = weekOptions,
                selectedValue = dialogSelectedValue,
                onValueChange = { newValue ->
                    dialogSelectedValue = newValue
                },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = {
                val weekNumber = if (dialogSelectedValue == optionOnVacationText) {
                    null
                } else {
                    dialogSelectedValue.filter { it.isDigit() }.toIntOrNull()
                }
                onConfirm(weekNumber)
            }) {
                Text(stringResource(Res.string.action_confirm))
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text(stringResource(Res.string.action_cancel))
            }
        }
    )
}

/**
 * 每周起始日选择器对话框
 */
@Composable
fun DayOfWeekPickerDialog(
    initialDayOfWeekInt: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val dayOfWeekMondayText = stringResource(Res.string.day_of_week_monday)
    val dayOfWeekSundayText = stringResource(Res.string.day_of_week_sunday)

    val dayOptionsMap = mapOf(
        dayOfWeekMondayText to DayOfWeek.MONDAY.isoDayNumber,
        dayOfWeekSundayText to DayOfWeek.SUNDAY.isoDayNumber
    )
    val dayOptions = dayOptionsMap.keys.toList()

    val initialSelectedDayText = dayOptionsMap.entries.firstOrNull { it.value == initialDayOfWeekInt }?.key
        ?: dayOfWeekMondayText

    var dialogSelectedText by remember { mutableStateOf(initialSelectedDayText) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.dialog_title_set_first_day_of_week)) },
        text = {
            NativeNumberPicker(
                values = dayOptions,
                selectedValue = dialogSelectedText,
                onValueChange = { newValue ->
                    dialogSelectedText = newValue
                },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = {
                val selectedDayInt = dayOptionsMap[dialogSelectedText] ?: DayOfWeek.MONDAY.isoDayNumber
                onConfirm(selectedDayInt)
            }) {
                Text(stringResource(Res.string.action_confirm))
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text(stringResource(Res.string.action_cancel))
            }
        }
    )
}

/**
 * 数字选择器对话框
 */
@Composable
private fun NumberPickerDialog(
    title: String,
    range: IntRange,
    initialValue: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var dialogSelectedValue by remember { mutableIntStateOf(initialValue.coerceIn(range)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            NativeNumberPicker(
                values = range.toList(),
                selectedValue = initialValue.coerceIn(range),
                onValueChange = { newValue ->
                    dialogSelectedValue = newValue
                },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(dialogSelectedValue) }) {
                Text(stringResource(Res.string.action_confirm))
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text(stringResource(Res.string.action_cancel))
            }
        }
    )
}