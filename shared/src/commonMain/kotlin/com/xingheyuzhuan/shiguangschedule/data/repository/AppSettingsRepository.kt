package com.xingheyuzhuan.shiguangschedule.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.xingheyuzhuan.shiguangschedule.data.db.main.CourseTableConfig
import com.xingheyuzhuan.shiguangschedule.data.db.main.CourseTableConfigDao
import com.xingheyuzhuan.shiguangschedule.data.db.main.CourseTableDao
import com.xingheyuzhuan.shiguangschedule.data.model.AppSettingsModel
import com.xingheyuzhuan.shiguangschedule.data.model.AppThemeMode
import com.xingheyuzhuan.shiguangschedule.data.model.schedule_style.ScheduleGridStyleProto
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.char
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single
import kotlin.time.Clock

/**
 * 应用配置领域仓库
 *
 * 核心职责：
 * 1. 协调全局偏好设置 (DataStore) 与课表物理配置 (Room) 之间的数据流。
 * 2. 提供时间维度计算算法（周次偏移、日期回溯）。
 */
@Single
class AppSettingsRepository(
    @Named("AppSettings") private val dataStore: DataStore<Preferences>,
    private val styleDataStore: DataStore<ScheduleGridStyleProto>,
    private val courseTableDao: CourseTableDao,
    private val courseTableConfigDao: CourseTableConfigDao,
    @Named("FilesDir") private val filesDir: Path,
    private val fileSystem: FileSystem
) {
    private val DATE_FORMATTER = LocalDate.Format {
        year()
        char('-')
        monthNumber()
        char('-')
        day()
    }

    /**
     * 课表配置模板
     * 当 DataStore 选中的课表在数据库中尚未初始化配置时，以此模板为基础进行创建。
     */
    private val COURSE_CONFIG_TEMPLATE = CourseTableConfig(
        courseTableId = "",
        showWeekends = false,
        semesterStartDate = null,
        semesterTotalWeeks = 20,
        firstDayOfWeek = DayOfWeek.MONDAY.isoDayNumber
    )

    // 应用全局设置 (DataStore)

    /**
     * 获取应用设置数据流。
     */
    fun getAppSettings(): Flow<AppSettingsModel> = dataStore.data.map { prefs ->
        val dbFirstTableId = courseTableDao.getFirstTableOnce()?.id ?: ""

        AppSettingsModel.fromPreferences(prefs, dbFirstTableId)
    }

    /**
     * 检查并执行旧版壁纸路径的“消费式迁移”
     * 只有当旧 styleDataStore 中有路径，且新架构未设置时才会触发，迁移后立即清空旧路径。
     */
    private suspend fun checkAndMigrateIfNeeded() {
        try {
            val styleProto = styleDataStore.data.first()
            val oldPath = styleProto.background_image_path

            if (!oldPath.isNullOrEmpty()) {
                val currentSettings = getAppSettingsOnce()

                if (currentSettings.backgroundImagePath.light.isEmpty() && currentSettings.backgroundImagePath.dark.isEmpty()) {
                    val updatedSettings = currentSettings.copy(
                        backgroundImagePath = currentSettings.backgroundImagePath.copy(
                            light = oldPath,
                            dark = oldPath
                        )
                    )
                    insertOrUpdateAppSettings(updatedSettings)
                }

                styleDataStore.updateData { currentProto ->
                    currentProto.copy(background_image_path = "")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 获取当前生效的壁纸图片绝对路径字符串。
     * 严格根据深浅色模式独立路径读取，互不干扰。
     *
     * @param isDarkTheme 当前界面是否处于深色模式
     * @return 壁纸文件的绝对路径字符串；若未设置壁纸或文件在磁盘上实际不存在则返回 null。
     */
    suspend fun getActiveWallpaperPath(isDarkTheme: Boolean): String? {
        checkAndMigrateIfNeeded()

        val appSettings = getAppSettingsOnce()

        val pathSetting = if (appSettings.wallpaperFollowDarkMode) {
            if (isDarkTheme) appSettings.backgroundImagePath.dark else appSettings.backgroundImagePath.light
        } else {
            // 固定背景模式：深浅色共用同一张背景图（优先取 light，兼容旧数据回退 dark）
            appSettings.backgroundImagePath.light.ifEmpty { appSettings.backgroundImagePath.dark }
        }

        return resolveValidWallpaperFile(pathSetting)
    }

    fun resolveValidWallpaperFile(rawPathStr: String?): String? {
        val rawPath = rawPathStr?.takeIf { it.isNotBlank() } ?: return null
        try {
            val path = rawPath.toPath()
            val absolutePath = if (path.isAbsolute) path else filesDir.resolve(path)
            if (fileSystem.exists(absolutePath)) {
                return absolutePath.toString()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    /**
     * 获取一次性的应用设置快照。
     */
    suspend fun getAppSettingsOnce(): AppSettingsModel {
        return getAppSettings().first()
    }

    /**
     * 判断指定日期是否处于假期（Holiday.isHoliday 为 true）中。
     *
     * @param date 待检测的日期
     * @return 如果该日期匹配到任何一个假期记录，则返回 true；否则返回 false。
     */
    suspend fun isDateInHoliday(date: LocalDate): Boolean {
        val appSettings = getAppSettingsOnce()
        return appSettings.holidays.any { holiday ->
            holiday.isHoliday && holiday.dates.contains(date)
        }
    }

    suspend fun updateThemeMode(mode: AppThemeMode) {
        dataStore.edit { prefs ->
            prefs[AppSettingsModel.KEY_THEME_MODE] = mode.value
        }
    }

    suspend fun updateAppUiFontScale(scale: Float) {
        dataStore.edit { prefs ->
            prefs[AppSettingsModel.KEY_APP_UI_FONT_SCALE] = scale.coerceIn(0.85f, 1.25f)
        }
    }

    suspend fun updateWallpaperVisuals(
        followDarkMode: Boolean? = null,
        blurDp: Float? = null,
        dimAlpha: Float? = null,
        glassEnabled: Boolean? = null,
        cardFrost: Float? = null,
        syncSchedule: Boolean? = null,
        syncToday: Boolean? = null,
        syncCampus: Boolean? = null,
        syncSettings: Boolean? = null
    ) {
        dataStore.edit { prefs ->
            followDarkMode?.let { prefs[AppSettingsModel.KEY_WALLPAPER_FOLLOW_DARK_MODE] = it }
            blurDp?.let { prefs[AppSettingsModel.KEY_WALLPAPER_BLUR_DP] = it.coerceIn(0f, 28f) }
            dimAlpha?.let { prefs[AppSettingsModel.KEY_WALLPAPER_DIM_ALPHA] = it.coerceIn(0f, 0.55f) }
            glassEnabled?.let { prefs[AppSettingsModel.KEY_WALLPAPER_GLASS_ENABLED] = it }
            cardFrost?.let { prefs[AppSettingsModel.KEY_WALLPAPER_CARD_FROST] = it.coerceIn(0f, 0.85f) }
            syncSchedule?.let { prefs[AppSettingsModel.KEY_WALLPAPER_SYNC_SCHEDULE] = it }
            syncToday?.let { prefs[AppSettingsModel.KEY_WALLPAPER_SYNC_TODAY] = it }
            syncCampus?.let { prefs[AppSettingsModel.KEY_WALLPAPER_SYNC_CAMPUS] = it }
            syncSettings?.let { prefs[AppSettingsModel.KEY_WALLPAPER_SYNC_SETTINGS] = it }
        }
    }

    /**
     * 更新应用设置。
     * 将对象解构并原子化地写入 DataStore。
     */
    suspend fun insertOrUpdateAppSettings(newSettings: AppSettingsModel) {
        dataStore.edit { prefs ->
            prefs[AppSettingsModel.KEY_CURRENT_COURSE_TABLE_ID] = newSettings.currentCourseTableId
            prefs[AppSettingsModel.KEY_REMINDER_ENABLED] = newSettings.reminderEnabled
            prefs[AppSettingsModel.KEY_REMIND_BEFORE_MINUTES] = newSettings.remindBeforeMinutes
            prefs[AppSettingsModel.KEY_HOLIDAYS_JSON] = Json.encodeToString(newSettings.holidays)
            prefs[AppSettingsModel.KEY_AUTO_MODE_ENABLED] = newSettings.autoModeEnabled
            prefs[AppSettingsModel.KEY_AUTO_CONTROL_MODE] = newSettings.autoControlMode.value
            prefs[AppSettingsModel.KEY_COMPAT_WEARABLE_SYNC] = newSettings.compatWearableSync
            prefs[AppSettingsModel.KEY_SHOW_NON_CURRENT_WEEK_COURSES] = newSettings.showNonCurrentWeekCourses
            prefs[AppSettingsModel.KEY_START_SCREEN] = newSettings.startScreen.value
            prefs[AppSettingsModel.KEY_THEME_MODE] = newSettings.themeMode.value
            prefs[AppSettingsModel.KEY_USE_DYNAMIC_COLOR] = newSettings.useDynamicColor
            prefs[AppSettingsModel.KEY_CUSTOM_LIGHT_PRIMARY] = newSettings.customPrimaryColor.light
            prefs[AppSettingsModel.KEY_CUSTOM_DARK_PRIMARY] = newSettings.customPrimaryColor.dark
            prefs[AppSettingsModel.KEY_BACKGROUND_IMAGE_PATH_LIGHT] = newSettings.backgroundImagePath.light
            prefs[AppSettingsModel.KEY_BACKGROUND_IMAGE_PATH_DARK] = newSettings.backgroundImagePath.dark
            prefs[AppSettingsModel.KEY_WALLPAPER_FOLLOW_DARK_MODE] = newSettings.wallpaperFollowDarkMode
            prefs[AppSettingsModel.KEY_WALLPAPER_BLUR_DP] = newSettings.wallpaperBlurDp.coerceIn(0f, 28f)
            prefs[AppSettingsModel.KEY_WALLPAPER_DIM_ALPHA] = newSettings.wallpaperDimAlpha.coerceIn(0f, 0.55f)
            prefs[AppSettingsModel.KEY_WALLPAPER_GLASS_ENABLED] = newSettings.wallpaperGlassEnabled
            prefs[AppSettingsModel.KEY_WALLPAPER_CARD_FROST] = newSettings.wallpaperCardFrost.coerceIn(0f, 0.85f)
            prefs[AppSettingsModel.KEY_RECENT_WALLPAPERS_JSON] = Json.encodeToString(
                newSettings.recentWallpapers.filter { it.isNotBlank() }.distinct().take(3)
            )
            prefs[AppSettingsModel.KEY_WALLPAPER_SYNC_SCHEDULE] = newSettings.wallpaperSyncSchedule
            prefs[AppSettingsModel.KEY_WALLPAPER_SYNC_TODAY] = newSettings.wallpaperSyncToday
            prefs[AppSettingsModel.KEY_WALLPAPER_SYNC_CAMPUS] = newSettings.wallpaperSyncCampus
            prefs[AppSettingsModel.KEY_WALLPAPER_SYNC_SETTINGS] = newSettings.wallpaperSyncSettings
            prefs[AppSettingsModel.KEY_DEVELOPER_MODE_ENABLED] = newSettings.developerModeEnabled
            prefs[AppSettingsModel.KEY_APP_UI_FONT_SCALE] = newSettings.appUiFontScale.coerceIn(0.85f, 1.25f)
        }
    }

    /**
     * 导出备份设置字典。
     * 自动遍历 BACKUP_KEYS 白名单，提取 DataStore 中现存的原始偏好键值字符串。
     */
    suspend fun getAppSettingsBackupMap(): Map<String, String> {
        val prefs = dataStore.data.first()
        val result = mutableMapOf<String, String>()

        AppSettingsModel.BACKUP_KEYS.forEach { key ->
            if (prefs.contains(key)) {
                prefs[key]?.let { value ->
                    result[key.name] = value.toString()
                }
            }
        }
        return result
    }

    /**
     * 从备份中恢复设置。
     * 严格根据 BACKUP_KEYS 白名单自动类型强转并更新 DataStore，绝不污染非白名单项。
     */
    suspend fun restoreAppSettingsFromBackupMap(backupMap: Map<String, String>) {
        dataStore.edit { prefs ->
            AppSettingsModel.BACKUP_KEYS.forEach { key ->
                val strValue = backupMap[key.name] ?: return@forEach

                when (key) {
                    AppSettingsModel.KEY_CURRENT_COURSE_TABLE_ID -> {
                        prefs[AppSettingsModel.KEY_CURRENT_COURSE_TABLE_ID] = strValue
                    }
                    AppSettingsModel.KEY_REMIND_BEFORE_MINUTES -> {
                        strValue.toIntOrNull()?.let { prefs[AppSettingsModel.KEY_REMIND_BEFORE_MINUTES] = it }
                    }
                    AppSettingsModel.KEY_HOLIDAYS_JSON -> {
                        prefs[AppSettingsModel.KEY_HOLIDAYS_JSON] = strValue
                    }
                    AppSettingsModel.KEY_SHOW_NON_CURRENT_WEEK_COURSES -> {
                        strValue.toBooleanStrictOrNull()?.let { prefs[AppSettingsModel.KEY_SHOW_NON_CURRENT_WEEK_COURSES] = it }
                    }
                    AppSettingsModel.KEY_CUSTOM_LIGHT_PRIMARY -> {
                        strValue.toLongOrNull()?.let { prefs[AppSettingsModel.KEY_CUSTOM_LIGHT_PRIMARY] = it }
                    }
                    AppSettingsModel.KEY_CUSTOM_DARK_PRIMARY -> {
                        strValue.toLongOrNull()?.let { prefs[AppSettingsModel.KEY_CUSTOM_DARK_PRIMARY] = it }
                    }
                }
            }
        }
    }

    // 课表具体物理配置 (Room)

    /**
     * 根据课表ID获取一次性配置快照。
     */
    suspend fun getCourseConfigOnce(tableId: String): CourseTableConfig? {
        return courseTableConfigDao.getConfigOnce(tableId)
    }

    /**
     * 根据课表ID实时获取配置数据流。
     */
    fun getCourseTableConfigFlow(courseTableId: String): Flow<CourseTableConfig?> {
        return courseTableConfigDao.getConfigById(courseTableId)
    }

    /**
     * 更新或插入特定课表的物理配置。
     */
    suspend fun insertOrUpdateCourseConfig(newConfig: CourseTableConfig) {
        val constrainedConfig = when {
            newConfig.firstDayOfWeek == DayOfWeek.SUNDAY.isoDayNumber -> {
                newConfig.copy(showWeekends = true)
            }
            !newConfig.showWeekends -> {
                newConfig.copy(firstDayOfWeek = DayOfWeek.MONDAY.isoDayNumber)
            }
            else -> newConfig
        }
        courseTableConfigDao.insertOrUpdate(constrainedConfig)
    }

    // 业务算法 (时间、周次计算)

    /**
     * 核心周次偏移算法。
     */
    fun getWeekIndexAtDate(
        targetDate: LocalDate,
        startDateStr: String?,
        firstDayOfWeekInt: Int
    ): Int? {
        if (startDateStr.isNullOrEmpty()) return null
        return try {
            val targetFirstDayOfWeek = DayOfWeek(firstDayOfWeekInt)
            val parsedStartDate = LocalDate.parse(startDateStr, DATE_FORMATTER)

            val alignedStartDate = getPreviousOrSameDayOfWeek(parsedStartDate, targetFirstDayOfWeek)
            val alignedTargetDate = getPreviousOrSameDayOfWeek(targetDate, targetFirstDayOfWeek)

            val diffDays = alignedTargetDate.toEpochDays() - alignedStartDate.toEpochDays()
            val diffWeeks = (diffDays / 7).toInt()
            diffWeeks + 1
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 基于当前数据库/DataStore状态计算当前自然周次。
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun calculateCurrentWeekFromDb(): Flow<Int?> = getAppSettings().flatMapLatest { appSettings ->
        val currentCourseId = appSettings.currentCourseTableId.ifEmpty {
            return@flatMapLatest flowOf(null)
        }
        courseTableConfigDao.getConfigById(currentCourseId).map { config ->
            if (config == null) return@map null
            val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
            val rawWeek = getWeekIndexAtDate(
                targetDate = today,
                startDateStr = config.semesterStartDate,
                firstDayOfWeekInt = config.firstDayOfWeek
            ) ?: return@map null
            if (rawWeek in 1..config.semesterTotalWeeks) rawWeek else null
        }
    }

    /**
     * 根据目标周数反推开学日期。
     */
    suspend fun setSemesterStartDateFromWeek(week: Int?) {
        val appSettings = getAppSettingsOnce()
        val currentCourseId = appSettings.currentCourseTableId.ifEmpty { return }

        val currentConfig = courseTableConfigDao.getConfigOnce(currentCourseId)
            ?: COURSE_CONFIG_TEMPLATE.copy(courseTableId = currentCourseId)

        val newStartDate = if (week != null) {
            calculateSemesterStartDate(week, currentConfig.firstDayOfWeek)
        } else {
            null
        }

        val updatedConfig = currentConfig.copy(semesterStartDate = newStartDate)
        courseTableConfigDao.insertOrUpdate(updatedConfig)
    }

    /**
     * 辅助函数：根据目标周数反推开学日期。
     */
    private fun calculateSemesterStartDate(week: Int, firstDayOfWeekInt: Int): String {
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val firstDayOfWeek = DayOfWeek(firstDayOfWeekInt)
        val startOfThisWeek = getPreviousOrSameDayOfWeek(today, firstDayOfWeek)
        val daysToSubtract = (week - 1) * 7
        val semesterStartDate = LocalDate.fromEpochDays(startOfThisWeek.toEpochDays() - daysToSubtract)
        return semesterStartDate.format(DATE_FORMATTER)
    }

    /**
     * 对齐日期到指定每周首日的指定星期几。
     */
    private fun getPreviousOrSameDayOfWeek(date: LocalDate, targetDayOfWeek: DayOfWeek): LocalDate {
        val currentDay = date.dayOfWeek.isoDayNumber
        val targetDay = targetDayOfWeek.isoDayNumber
        val daysToSubtract = if (currentDay >= targetDay) {
            currentDay - targetDay
        } else {
            7 - (targetDay - currentDay)
        }
        return LocalDate.fromEpochDays(date.toEpochDays() - daysToSubtract)
    }
}