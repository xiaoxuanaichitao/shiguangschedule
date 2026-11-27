package com.xingheyuzhuan.shiguangschedule

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.metadata
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.xingheyuzhuan.shiguangschedule.cumt.CumtAutoLoginCoordinator
import com.xingheyuzhuan.shiguangschedule.data.model.AppSettingsModel
import com.xingheyuzhuan.shiguangschedule.data.model.StartScreen
import com.xingheyuzhuan.shiguangschedule.tool.ExternalFileManager
import com.xingheyuzhuan.shiguangschedule.ui.theme.LocalIsDarkTheme
import com.xingheyuzhuan.shiguangschedule.ui.components.AdaptiveNavigationScaffold
import com.xingheyuzhuan.shiguangschedule.ui.cumt.CampusDiscoverScreen
import com.xingheyuzhuan.shiguangschedule.ui.cumt.CumtAiCourseImportScreen
import com.xingheyuzhuan.shiguangschedule.ui.cumt.CumtAiFloatingOverlay
import com.xingheyuzhuan.shiguangschedule.ui.cumt.CumtAuthScreen
import com.xingheyuzhuan.shiguangschedule.ui.cumt.CumtCampusCardScreen
import com.xingheyuzhuan.shiguangschedule.ui.cumt.CumtCampusNetScreen
import com.xingheyuzhuan.shiguangschedule.ui.cumt.CumtCourseImportScreen
import com.xingheyuzhuan.shiguangschedule.ui.cumt.CumtDormPowerScreen
import com.xingheyuzhuan.shiguangschedule.ui.cumt.CumtExamScreen
import com.xingheyuzhuan.shiguangschedule.ui.cumt.CumtJwxtExtractScreen
import com.xingheyuzhuan.shiguangschedule.ui.cumt.CumtLibraryScreen
import com.xingheyuzhuan.shiguangschedule.ui.cumt.CumtSchoolBusScreen
import com.xingheyuzhuan.shiguangschedule.ui.cumt.CumtScoreMappingScreen
import com.xingheyuzhuan.shiguangschedule.ui.cumt.CumtScoreScreen
import com.xingheyuzhuan.shiguangschedule.ui.schedule.WeeklyScheduleScreen
import com.xingheyuzhuan.shiguangschedule.ui.schedule.components.ScheduleWallpaperBackdrop
import com.xingheyuzhuan.shiguangschedule.ui.schedule.components.WallpaperVisualConfig
import com.xingheyuzhuan.shiguangschedule.ui.schoolselection.web.WebViewScreen
import com.xingheyuzhuan.shiguangschedule.ui.settings.SettingsScreen
import com.xingheyuzhuan.shiguangschedule.ui.settings.SettingsViewModel
import com.xingheyuzhuan.shiguangschedule.ui.settings.additional.LanguageSettingScreen
import com.xingheyuzhuan.shiguangschedule.ui.settings.additional.MoreOptionsScreen
import com.xingheyuzhuan.shiguangschedule.ui.settings.additional.OpenSourceLicensesScreen
import com.xingheyuzhuan.shiguangschedule.ui.settings.backup.BackupScreen
import com.xingheyuzhuan.shiguangschedule.ui.settings.contribution.ContributionScreen
import com.xingheyuzhuan.shiguangschedule.ui.settings.conversion.CourseTableConversionScreen
import com.xingheyuzhuan.shiguangschedule.ui.settings.course.AddEditCourseScreen
import com.xingheyuzhuan.shiguangschedule.ui.settings.coursemanagement.CourseInstanceListScreen
import com.xingheyuzhuan.shiguangschedule.ui.settings.coursemanagement.CourseNameListScreen
import com.xingheyuzhuan.shiguangschedule.ui.settings.coursetables.ManageCourseTablesScreen
import com.xingheyuzhuan.shiguangschedule.ui.settings.holiday.HolidayManagementScreen
import com.xingheyuzhuan.shiguangschedule.ui.settings.notification.NotificationSettingsScreen
import com.xingheyuzhuan.shiguangschedule.ui.settings.quickactions.QuickActionsScreen
import com.xingheyuzhuan.shiguangschedule.ui.settings.quickactions.delete.QuickDeleteScreen
import com.xingheyuzhuan.shiguangschedule.ui.settings.quickactions.tweaks.TweakScheduleScreen
import com.xingheyuzhuan.shiguangschedule.ui.settings.style.StyleSettingsScreen
import com.xingheyuzhuan.shiguangschedule.ui.settings.style.widget.WidgetStyleSettingsScreen
import com.xingheyuzhuan.shiguangschedule.ui.settings.themesettings.ThemeSettingsScreen
import com.xingheyuzhuan.shiguangschedule.ui.settings.time.ComboScheduleEditScreen
import com.xingheyuzhuan.shiguangschedule.ui.settings.time.SingleScheduleEditScreen
import com.xingheyuzhuan.shiguangschedule.ui.settings.time.TimeScheduleManagementScreen
import com.xingheyuzhuan.shiguangschedule.ui.theme.ShiguangScheduleTheme
import com.xingheyuzhuan.shiguangschedule.ui.theme.SyncImmersiveStatusBarIcons
import com.xingheyuzhuan.shiguangschedule.ui.today.TodayScheduleScreen
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun App() {
    val viewModel: SettingsViewModel = koinViewModel()
    val state by viewModel.uiState.collectAsState()
    val autoLoginCoordinator: CumtAutoLoginCoordinator = koinInject()

    LaunchedEffect(Unit) {
        autoLoginCoordinator.tryAutoLoginOnStartup()
    }

    if (state.isReady) {
        ShiguangScheduleTheme(settings = state.appSettings) {
            val startDest = remember(state.appSettings.startScreen) {
                when (state.appSettings.startScreen) {
                    StartScreen.COURSE_SCHEDULE -> Destination.CourseSchedule
                    StartScreen.TODAY_SCHEDULE -> Destination.TodaySchedule
                }
            }
            AppNavigation(
                startDestination = startDest,
                appSettings = state.appSettings
            )
        }
    } else {
        Surface(modifier = Modifier.fillMaxSize()) {}
    }
}

@Composable
fun AppNavigation(
    startDestination: Destination,
    appSettings: AppSettingsModel = AppSettingsModel()
) {
    val backStack = rememberNavBackStack(
        configuration = navSavedStateConfig,
        startDestination
    )

    val currentDestination = backStack.lastOrNull() as? Destination ?: startDestination

    var navHideFraction by remember { mutableFloatStateOf(0f) }

    val pendingFile by ExternalFileManager.pendingFile.collectAsState()

    val onNavigate: (Destination) -> Unit = remember(backStack) {
        { dest ->
            if (dest.isMainScreen) {
                if (backStack.lastOrNull() != dest) {
                    backStack.clear()
                    backStack.add(dest)
                }
            } else {
                if (backStack.lastOrNull() != dest) {
                    backStack.add(dest)
                }
            }
        }
    }

    LaunchedEffect(pendingFile) {
        val target = pendingFile?.targetDestination
        if (target != null && backStack.lastOrNull() != target) {
            onNavigate(target)
        }
    }

    val onBack: () -> Unit = remember(backStack) {
        {
            if (backStack.size > 1) {
                backStack.removeAt(backStack.lastIndex)
            }
        }
    }

    val animSpec = tween<IntOffset>(300)
    val isDark = LocalIsDarkTheme.current
    val activeWallpaperRaw = if (appSettings.wallpaperFollowDarkMode) {
        if (isDark) appSettings.backgroundImagePath.dark else appSettings.backgroundImagePath.light
    } else {
        appSettings.backgroundImagePath.light.ifEmpty { appSettings.backgroundImagePath.dark }
    }
    val sharedWallpaperVisuals = remember(
        activeWallpaperRaw,
        appSettings.wallpaperBlurDp,
        appSettings.wallpaperDimAlpha,
        appSettings.wallpaperGlassEnabled,
        appSettings.wallpaperCardFrost
    ) {
        WallpaperVisualConfig(
            wallpaperPath = activeWallpaperRaw.ifBlank { null },
            blurDp = appSettings.wallpaperBlurDp,
            dimAlpha = appSettings.wallpaperDimAlpha,
            glassEnabled = appSettings.wallpaperGlassEnabled,
            cardFrost = appSettings.wallpaperCardFrost
        )
    }

    fun isSyncedWithWallpaper(dest: Destination): Boolean {
        if (!sharedWallpaperVisuals.hasWallpaper) return false
        return when (dest) {
            Destination.CourseSchedule, Destination.TodaySchedule -> true
            Destination.CampusDiscover -> appSettings.wallpaperSyncCampus
            Destination.Settings -> appSettings.wallpaperSyncSettings
            else -> false
        }
    }

    val currentUsesSharedWallpaper = isSyncedWithWallpaper(currentDestination)

    SyncImmersiveStatusBarIcons(
        forceLightContent = currentUsesSharedWallpaper,
        darkTheme = isDark
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // 全局常驻底层共享壁纸层：切 Tab 时绝不销毁重建，彻底消除闪屏与壁纸跟随横滑
        if (sharedWallpaperVisuals.hasWallpaper) {
            ScheduleWallpaperBackdrop(
                visuals = sharedWallpaperVisuals,
                showLeftAxisScrim = currentDestination == Destination.CourseSchedule || currentDestination == Destination.TodaySchedule
            )
        }

        AdaptiveNavigationScaffold(
            currentDestination = currentDestination,
            onTabSelected = onNavigate,
            showNavigation = currentDestination.isMainScreen,
            navHideFractionProvider = { navHideFraction },
            isFrostedGlass = currentUsesSharedWallpaper
        ) { _ ->
            NavDisplay(
                backStack = backStack,
                onBack = onBack,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    val fromMain = (initialState.key as? Destination)?.isMainScreen == true
                    val toMain = (targetState.key as? Destination)?.isMainScreen == true
                    if (fromMain && toMain) {
                        fadeIn(animationSpec = tween(150)) togetherWith fadeOut(animationSpec = tween(110))
                    } else {
                        slideInHorizontally(initialOffsetX = { it }, animationSpec = animSpec) togetherWith
                                slideOutHorizontally(targetOffsetX = { -it / 3 }, animationSpec = animSpec) + fadeOut()
                    }
                },
                popTransitionSpec = {
                    val fromMain = (initialState.key as? Destination)?.isMainScreen == true
                    val toMain = (targetState.key as? Destination)?.isMainScreen == true
                    if (fromMain && toMain) {
                        fadeIn(animationSpec = tween(150)) togetherWith fadeOut(animationSpec = tween(110))
                    } else {
                        slideInHorizontally(initialOffsetX = { -it / 3 }, animationSpec = animSpec) + fadeIn() togetherWith
                                slideOutHorizontally(targetOffsetX = { it }, animationSpec = animSpec)
                    }
                },
                predictivePopTransitionSpec = {
                    slideInHorizontally(initialOffsetX = { -it / 3 }, animationSpec = animSpec) + fadeIn() togetherWith
                            slideOutHorizontally(targetOffsetX = { it }, animationSpec = animSpec)
                },
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator()
                )
            ) { key ->
                val destination = key as Destination
                val entryUsesWallpaper = isSyncedWithWallpaper(destination)

                NavEntry(
                    key = key,
                    metadata = metadata {
                        put(ShiguangNavMetadata.IsMainScreenKey, destination.isMainScreen)
                    }
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = if (entryUsesWallpaper) Color.Transparent else MaterialTheme.colorScheme.background
                    ) {
                        ScreenContent(
                            targetDest = destination,
                            onNavigate = onNavigate,
                            onBack = onBack,
                            onNavHideFractionChanged = { navHideFraction = it },
                            hasSharedWallpaper = entryUsesWallpaper
                        )
                    }
                }
            }
        }

        // Notion 风格全局可拖拽 AI 悬浮小球与半屏灵动对话抽屉
        CumtAiFloatingOverlay(
            currentDestination = currentDestination,
            onNavigate = onNavigate
        )
    }
}

@Composable
fun ScreenContent(
    targetDest: Destination,
    onNavigate: (Destination) -> Unit,
    onBack: () -> Unit,
    onNavHideFractionChanged: (Float) -> Unit,
    hasSharedWallpaper: Boolean = false
) {
    when (targetDest) {
        Destination.CourseSchedule -> WeeklyScheduleScreen(
            onNavigate = onNavigate,
            onBack = onBack,
            onNavHideFractionChanged = onNavHideFractionChanged,
            hasSharedWallpaper = hasSharedWallpaper
        )
        Destination.CampusDiscover -> CampusDiscoverScreen(
            onNavigate = onNavigate,
            hasSharedWallpaper = hasSharedWallpaper
        )
        Destination.Settings -> SettingsScreen(
            onNavigate = onNavigate,
            onBack = onBack,
            hasSharedWallpaper = hasSharedWallpaper
        )
        Destination.TodaySchedule -> TodayScheduleScreen(
            onNavigate = onNavigate,
            onBack = onBack,
            hasSharedWallpaper = hasSharedWallpaper
        )
        Destination.ManageCourseTables -> ManageCourseTablesScreen(onBack)
        Destination.CourseTableConversion -> CourseTableConversionScreen(onNavigate, onBack)
        Destination.NotificationSettings -> NotificationSettingsScreen(onBack)
        Destination.MoreOptions -> MoreOptionsScreen(onNavigate, onBack)
        Destination.OpenSourceLicenses -> OpenSourceLicensesScreen(onBack)
        Destination.QuickActions -> QuickActionsScreen(onNavigate, onBack)
        Destination.TweakSchedule -> TweakScheduleScreen(onBack)
        Destination.ContributionList -> ContributionScreen(onBack)
        Destination.CourseManagementList -> CourseNameListScreen(onNavigate, onBack)
        Destination.StyleSettings -> StyleSettingsScreen(onNavigate,onBack)
        Destination.QuickDelete -> QuickDeleteScreen(onBack)
        Destination.ThemeSettings -> ThemeSettingsScreen(onBack)
        Destination.BackupAndRestore -> BackupScreen(onBack)
        Destination.LanguageSettings -> LanguageSettingScreen(onBack)
        Destination.WidgetStyleSettings -> WidgetStyleSettingsScreen(onBack)
        Destination.HolidayManagement -> HolidayManagementScreen(onBack = onBack)

        Destination.CumtAuth -> CumtAuthScreen(onNavigate, onBack)
        Destination.CumtCourseImport -> CumtCourseImportScreen(onNavigate, onBack)
        Destination.CumtAiCourseImport -> CumtAiCourseImportScreen(onNavigate, onBack)
        is Destination.CumtJwxtExtract -> CumtJwxtExtractScreen(targetDest.mode, onBack)
        Destination.CumtExams -> CumtExamScreen(onNavigate, onBack)
        Destination.CumtScores -> CumtScoreScreen(onNavigate, onBack)
        Destination.CumtScoreMappings -> CumtScoreMappingScreen(onBack)
        Destination.CumtLibrary -> CumtLibraryScreen(onNavigate, onBack)
        Destination.CumtCampusCard -> CumtCampusCardScreen(onNavigate, onBack)
        Destination.CumtDormPower -> CumtDormPowerScreen(onNavigate, onBack)
        Destination.CumtCampusNet -> CumtCampusNetScreen(onNavigate, onBack)
        Destination.CumtSchoolBus -> CumtSchoolBusScreen(onNavigate, onBack)

        Destination.TimeScheduleManagement -> TimeScheduleManagementScreen(
            onBack = onBack,
            onEditSingleSchedule = { tableId, isPublic, copyFromId ->
                onNavigate(Destination.SingleScheduleEdit(tableId, isPublic, copyFromId))
            },
            onEditComboSchedule = { comboId, copyFromId ->
                onNavigate(Destination.ComboScheduleEdit(comboId, copyFromId))
            }
        )

        // 单一/公共作息编辑页面路由
        is Destination.SingleScheduleEdit -> SingleScheduleEditScreen(
            tableId = targetDest.tableId,
            isPublic = targetDest.isPublic,
            copyFromId = targetDest.copyFromId,
            onBack = onBack
        )

        // 组合作息编辑页面路由
        is Destination.ComboScheduleEdit -> ComboScheduleEditScreen(
            comboId = targetDest.comboId,
            copyFromId = targetDest.copyFromId,
            onBack = onBack
        )

        is Destination.WebView -> WebViewScreen(
            onNavigate, onBack, targetDest.initialUrl, targetDest.assetJsPath
        )
        is Destination.AddEditCourse -> AddEditCourseScreen(
            onBack, targetDest.courseId
        )
        is Destination.CourseManagementDetail -> CourseInstanceListScreen(
            targetDest.courseName, onBack, onNavigate
        )
    }
}