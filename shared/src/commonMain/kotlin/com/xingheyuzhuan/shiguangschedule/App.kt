package com.xingheyuzhuan.shiguangschedule

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.metadata
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.xingheyuzhuan.shiguangschedule.cumt.CumtAutoLoginCoordinator
import com.xingheyuzhuan.shiguangschedule.cumt.CumtLocalRepository
import com.xingheyuzhuan.shiguangschedule.data.model.AppSettingsModel
import com.xingheyuzhuan.shiguangschedule.data.model.StartScreen
import com.xingheyuzhuan.shiguangschedule.tool.ExternalFileManager
import com.xingheyuzhuan.shiguangschedule.tool.UpdateChecker
import com.xingheyuzhuan.shiguangschedule.tool.UpdateDownloadState
import com.xingheyuzhuan.shiguangschedule.tool.UpdatePlatform
import com.xingheyuzhuan.shiguangschedule.tool.UpdateStatus
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
import com.xingheyuzhuan.shiguangschedule.ui.cumt.CumtOnboardingDialog
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
import com.xingheyuzhuan.shiguangschedule.ui.settings.additional.UpdateResultDialog
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.qualifier.named

@Composable
fun App() {
    val viewModel: SettingsViewModel = koinViewModel()
    val state by viewModel.uiState.collectAsState()
    val autoLoginCoordinator: CumtAutoLoginCoordinator = koinInject()
    val updateChecker: UpdateChecker = koinInject()
    val localRepo: CumtLocalRepository = koinInject()
    val appVersionName: String = koinInject(named("AppVersionName"))
    val appVersionCode: Int = koinInject(named("AppVersionCode"))
    val appScope = rememberCoroutineScope()

    var startupUpdateFound by remember { mutableStateOf<UpdateStatus.Found?>(null) }
    var startupDownloadState by remember { mutableStateOf<UpdateDownloadState>(UpdateDownloadState.Idle) }

    LaunchedEffect(Unit) {
        autoLoginCoordinator.tryAutoLoginOnStartup()
    }

    // 启动静默极速检查更新（仅延迟 300ms 避开首帧绘制，多线路并发竞速秒级弹窗）
    LaunchedEffect(Unit) {
        delay(300L)
        val result = runCatching {
            updateChecker.checkUpdate(UpdatePlatform.GITHUB, appVersionName, appVersionCode)
        }.getOrNull()
        if (result is UpdateStatus.Found) {
            val ignoredTag = runCatching { localRepo.getIgnoredUpdateVersion() }.getOrDefault("")
            if (ignoredTag != result.versionName) {
                startupUpdateFound = result
                startupDownloadState = UpdateDownloadState.Idle
            }
        }
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

            startupUpdateFound?.let { found ->
                UpdateResultDialog(
                    showDialog = true,
                    updateStatus = found,
                    downloadState = startupDownloadState,
                    onDismiss = {
                        startupUpdateFound = null
                        startupDownloadState = UpdateDownloadState.Idle
                    },
                    onDownloadClick = { target ->
                        appScope.launch {
                            updateChecker.downloadAndInstall(target) { dlState ->
                                startupDownloadState = dlState
                            }
                        }
                    },
                    onBrowserDownloadClick = { pageUrl ->
                        updateChecker.launchUpdate(pageUrl)
                    },
                    onIgnoreVersion = { tag ->
                        startupUpdateFound = null
                        startupDownloadState = UpdateDownloadState.Idle
                        appScope.launch {
                            localRepo.setIgnoredUpdateVersion(tag)
                        }
                    }
                )
            }
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

    // 4 个主 Tab 独立保活：首次访问后驻留组合树与 ViewModel，切换今日/周课表/校园/我的零重建零卡顿
    var activeMainTab by remember { mutableStateOf(startDestination) }
    val visitedMainTabs = remember { mutableStateListOf(startDestination) }

    val isAtMainRoot = backStack.size == 1 && (backStack.firstOrNull() as? Destination)?.isMainScreen == true
    val currentDestination = if (isAtMainRoot) {
        activeMainTab
    } else {
        backStack.lastOrNull() as? Destination ?: activeMainTab
    }

    var navHideFraction by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(activeMainTab, isAtMainRoot) {
        if (!isAtMainRoot || activeMainTab != Destination.CourseSchedule) {
            navHideFraction = 0f
        }
    }

    val pendingFile by ExternalFileManager.pendingFile.collectAsState()

    val onNavigate: (Destination) -> Unit = remember(backStack) {
        { dest ->
            if (dest.isMainScreen) {
                if (dest !in visitedMainTabs) {
                    visitedMainTabs.add(dest)
                }
                activeMainTab = dest
                while (backStack.size > 1) {
                    backStack.removeAt(backStack.lastIndex)
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
        if (target != null && currentDestination != target) {
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

    val animSpec = tween<IntOffset>(280)
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
            Destination.CourseSchedule -> appSettings.wallpaperSyncSchedule
            Destination.TodaySchedule -> appSettings.wallpaperSyncToday
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

    val localRepo: CumtLocalRepository = koinInject()
    val navScope = rememberCoroutineScope()
    var showOnboarding by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val completed = runCatching { localRepo.hasCompletedOnboarding() }.getOrDefault(true)
        if (!completed) {
            showOnboarding = true
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 全局常驻底层共享壁纸层：切 Tab 时绝不销毁重建，彻底消除闪屏与壁纸跟随横滑
        if (sharedWallpaperVisuals.hasWallpaper) {
            ScheduleWallpaperBackdrop(
                visuals = sharedWallpaperVisuals,
                showLeftAxisScrim = currentDestination == Destination.CourseSchedule || currentDestination == Destination.TodaySchedule
            )
        }
        // 当当前页面关闭了壁纸同步（如校园 / 我的）时，用纯色背景覆盖整屏含平板左侧导航栏区域，防止壁纸从左侧透出
        if (sharedWallpaperVisuals.hasWallpaper && !currentUsesSharedWallpaper) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
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
                    slideInHorizontally(initialOffsetX = { it }, animationSpec = animSpec) togetherWith
                            slideOutHorizontally(targetOffsetX = { -it / 3 }, animationSpec = animSpec) + fadeOut()
                },
                popTransitionSpec = {
                    slideInHorizontally(initialOffsetX = { -it / 3 }, animationSpec = animSpec) + fadeIn() togetherWith
                            slideOutHorizontally(targetOffsetX = { it }, animationSpec = animSpec)
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

                NavEntry(
                    key = key,
                    metadata = metadata {
                        put(ShiguangNavMetadata.IsMainScreenKey, destination.isMainScreen)
                    }
                ) {
                    if (destination.isMainScreen) {
                        MainTabsPersistentHost(
                            visitedTabs = visitedMainTabs,
                            activeTab = activeMainTab,
                            isTabWallpaperSynced = ::isSyncedWithWallpaper,
                            onNavigate = onNavigate,
                            onBack = onBack,
                            onCourseScheduleNavHideChanged = { frac ->
                                if (activeMainTab == Destination.CourseSchedule) {
                                    navHideFraction = frac
                                }
                            }
                        )
                    } else {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.background
                        ) {
                            ScreenContent(
                                targetDest = destination,
                                onNavigate = onNavigate,
                                onBack = onBack,
                                onNavHideFractionChanged = { navHideFraction = it },
                                hasSharedWallpaper = false
                            )
                        }
                    }
                }
            }
        }

        // Notion 风格全局可拖拽 AI 悬浮小球与半屏灵动对话抽屉
        CumtAiFloatingOverlay(
            currentDestination = currentDestination,
            onNavigate = onNavigate
        )

        if (showOnboarding) {
            CumtOnboardingDialog(
                onDismiss = {
                    showOnboarding = false
                    navScope.launch { localRepo.markOnboardingCompleted() }
                },
                onNavigateAndClose = { dest ->
                    showOnboarding = false
                    navScope.launch { localRepo.markOnboardingCompleted() }
                    onNavigate(dest)
                }
            )
        }
    }
}

/**
 * 零开销主 Tab 保活容器：
 * 已访问的 4 个主 Tab 常驻组合树并保留各自 ViewModel/滚动位置，
 * 非激活 Tab 跳过测量、布局与绘制（零 GPU 过绘、零触摸穿透），切换今日↔课表毫秒级瞬切。
 */
@Composable
private fun MainTabsPersistentHost(
    visitedTabs: List<Destination>,
    activeTab: Destination,
    isTabWallpaperSynced: (Destination) -> Boolean,
    onNavigate: (Destination) -> Unit,
    onBack: () -> Unit,
    onCourseScheduleNavHideChanged: (Float) -> Unit
) {
    val activeIdx = visitedTabs.indexOf(activeTab).coerceAtLeast(0)
    Layout(
        modifier = Modifier.fillMaxSize(),
        content = {
            visitedTabs.forEach { tab ->
                key(tab) {
                    val tabUsesWallpaper = isTabWallpaperSynced(tab)
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = if (tabUsesWallpaper) Color.Transparent else MaterialTheme.colorScheme.background
                    ) {
                        ScreenContent(
                            targetDest = tab,
                            onNavigate = onNavigate,
                            onBack = onBack,
                            onNavHideFractionChanged = onCourseScheduleNavHideChanged,
                            hasSharedWallpaper = tabUsesWallpaper
                        )
                    }
                }
            }
        }
    ) { measurables, constraints ->
        val activePlaceable = measurables.getOrNull(activeIdx)?.measure(constraints)
        layout(constraints.maxWidth, constraints.maxHeight) {
            activePlaceable?.placeRelative(0, 0)
        }
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