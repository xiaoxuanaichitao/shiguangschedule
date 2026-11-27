package com.xingheyuzhuan.shiguangschedule.data.model

import androidx.compose.ui.graphics.toArgb
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.xingheyuzhuan.shiguangschedule.ui.theme.DefaultThemeColor
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.StringResource
import shiguangschedule.shared.generated.resources.Res
import shiguangschedule.shared.generated.resources.nav_course_schedule
import shiguangschedule.shared.generated.resources.nav_today_schedule
import shiguangschedule.shared.generated.resources.theme_dark
import shiguangschedule.shared.generated.resources.theme_follow_system
import shiguangschedule.shared.generated.resources.theme_light

/**
 * 通用的深浅色配置包装类
 *
 * @param light 浅色模式下的配置项
 * @param dark 深色模式下的配置项
 */
@Serializable
data class LightDarkValue<T>(
    val light: T,
    val dark: T
)

/**
 * 节假日/调休 数据结构
 *
 * @property name 名称字符字段
 * @property isHoliday 是否是假期 (true: 假期/放假; false: 调休/补班)
 * @property dates 所属日期集合 (KMP 官方库强类型，默认以 ISO-8601 "YYYY-MM-DD" 序列化存储)
 */
@Serializable
data class Holiday(
    val name: String,
    val isHoliday: Boolean = true,
    val dates: Set<LocalDate> = emptySet()
)

/**
 * 上课时的自动化控制模式枚举
 */
enum class AutoControlMode(val value: String) {
    /** 请勿打扰模式 */
    DND("DND"),

    /** 静音模式 */
    SILENT("SILENT");

    companion object {
        /**
         * 根据字符串获取对应的枚举值，如果匹配失败则返回默认的 DND 模式
         */
        fun fromString(value: String?): AutoControlMode {
            return entries.find { it.value == value } ?: DND
        }
    }
}

/**
 * 可选的启动页面枚举
 */
enum class StartScreen(val value: String, val labelRes: StringResource) {
    /** 周课表 */
    COURSE_SCHEDULE("COURSE_SCHEDULE", Res.string.nav_course_schedule),

    /** 今日课表 */
    TODAY_SCHEDULE("TODAY_SCHEDULE", Res.string.nav_today_schedule);

    companion object {
        fun fromString(value: String?): StartScreen {
            return entries.find { it.value == value } ?: COURSE_SCHEDULE
        }
    }
}

/**
 * 应用主题模式枚举
 */
enum class AppThemeMode(val value: String, val labelRes: StringResource) {
    /** 跟随系统 */
    FOLLOW_SYSTEM("FOLLOW_SYSTEM", Res.string.theme_follow_system),

    /** 浅色模式 */
    LIGHT("LIGHT", Res.string.theme_light),

    /** 深色模式 */
    DARK("DARK", Res.string.theme_dark);

    companion object {
        fun fromString(value: String?): AppThemeMode? {
            return entries.find { it.value == value }
        }
    }
}

/**
 * 应用全局设置业务模型（DataStore 专用）
 * 集中管理业务字段、存储键 (Keys) 以及默认值。
 */
data class AppSettingsModel(
    /** 当前正在使用的课表 ID */
    val currentCourseTableId: String = "",

    /** 是否开启上课前提醒 */
    val reminderEnabled: Boolean = false,

    /** 提前提醒的时间（分钟） */
    val remindBeforeMinutes: Int = 15,

    /** 节假日及调休列表 */
    val holidays: List<Holiday> = emptyList(),

    /** 自动化模式的总开关 */
    val autoModeEnabled: Boolean = false,

    /** 自动化控制的具体模式，限定为 [AutoControlMode] */
    val autoControlMode: AutoControlMode = AutoControlMode.DND,

    /**
     * 兼容穿戴设备同步通知的开关
     * true: 开启兼容模式（关闭 Ongoing，方便手环抓取）
     * false: 关闭兼容模式（默认，使用 Android 16 实时更新特性）
     */
    val compatWearableSync: Boolean = false,

    /** 是否显示非本周课程 */
    val showNonCurrentWeekCourses: Boolean = false,

    /** 应用启动时显示的页面 */
    val startScreen: StartScreen = StartScreen.COURSE_SCHEDULE,

    /** 应用主题模式 */
    val themeMode: AppThemeMode = AppThemeMode.FOLLOW_SYSTEM,

    /** 是否开启动态取色 (Material You) */
    val useDynamicColor: Boolean = false,

    /** 自定义主题主色（包含浅色和深色模式） */
    val customPrimaryColor: LightDarkValue<Long> = LightDarkValue(
        light = DefaultThemeColor.toArgb().toLong(),
        dark = DefaultThemeColor.toArgb().toLong()
    ),

    /** 背景壁纸路径 (包含浅色和深色模式，存储在私有目录下的绝对路径) */
    val backgroundImagePath: LightDarkValue<String> = LightDarkValue(
        light = "",
        dark = ""
    ),

    /** 壁纸是否跟随深浅色模式切换（false = 固定背景深浅共用，true = 浅色/深色独立背景） */
    val wallpaperFollowDarkMode: Boolean = false,

    /** 背景壁纸高斯模糊半径（0f~28f dp） */
    val wallpaperBlurDp: Float = 0f,

    /** 背景壁纸护眼压暗遮罩浓度（0f~0.55f） */
    val wallpaperDimAlpha: Float = 0.06f,

    /** 是否开启课程卡片液态毛玻璃高光质感（默认关闭，保持最高帧率流畅度） */
    val wallpaperGlassEnabled: Boolean = false,

    /** 课程卡片背景柔雾防花底浓度（0f~0.85f，解决花哨壁纸下卡片文字发花问题） */
    val wallpaperCardFrost: Float = 0.28f,

    /** 最近从相册选择的壁纸历史（最多保留最近 3 张） */
    val recentWallpapers: List<String> = emptyList(),

    /** 自定义壁纸是否同步应用到「校园」页面 */
    val wallpaperSyncCampus: Boolean = false,

    /** 自定义壁纸是否同步应用到「我的 / 设置」页面 */
    val wallpaperSyncSettings: Boolean = false,

    /** 开发者功能总开关（默认关闭） */
    val developerModeEnabled: Boolean = false,

    /** 软件全局界面字号倍率（0.85f~1.25f，仅控制应用 UI 字体，不影响课表网格字号） */
    val appUiFontScale: Float = 1.0f,
) {
    /**
     * 将 DataStore 的 Key 定义在伴生对象中。
     * 这样在 Repository 中可以直接通过 AppSettingsModel.KEY_xxx 访问，
     * 避免了修改一处逻辑需要动多个文件的问题。
     */
    companion object {
        /** 当前正在使用的课表 ID 的存储键 */
        val KEY_CURRENT_COURSE_TABLE_ID = stringPreferencesKey("current_course_table_id")
        /** 是否开启上课前提醒的存储键 */
        val KEY_REMINDER_ENABLED = booleanPreferencesKey("reminder_enabled")
        /** 提前提醒时间（分钟）的存储键 */
        val KEY_REMIND_BEFORE_MINUTES = intPreferencesKey("remind_before_minutes")
        /** 节假日及调休列表 JSON 字符串的存储键 */
        val KEY_HOLIDAYS_JSON = stringPreferencesKey("holidays_json")
        /** 自动化模式总开关的存储键 */
        val KEY_AUTO_MODE_ENABLED = booleanPreferencesKey("auto_mode_enabled")
        /** 自动化控制具体模式的存储键 */
        val KEY_AUTO_CONTROL_MODE = stringPreferencesKey("auto_control_mode")
        /** 兼容穿戴设备同步通知开关的存储键 */
        val KEY_COMPAT_WEARABLE_SYNC = booleanPreferencesKey("compat_wearable_sync")
        /** 是否显示非本周课程的存储键 */
        val KEY_SHOW_NON_CURRENT_WEEK_COURSES = booleanPreferencesKey("show_non_current_week_courses")
        /** 应用启动页面的存储键 */
        val KEY_START_SCREEN = stringPreferencesKey("start_screen")
        /** 应用主题模式的存储键 */
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        /** 是否开启动态取色 (Material You) 的存储键 */
        val KEY_USE_DYNAMIC_COLOR = booleanPreferencesKey("use_dynamic_color")
        /** 自定义主题浅色主色的存储键 */
        val KEY_CUSTOM_LIGHT_PRIMARY = longPreferencesKey("custom_light_primary")
        /** 自定义主题深色主色的存储键 */
        val KEY_CUSTOM_DARK_PRIMARY = longPreferencesKey("custom_dark_primary")
        /** 浅色模式背景壁纸路径的存储键 */
        val KEY_BACKGROUND_IMAGE_PATH_LIGHT = stringPreferencesKey("background_image_path_light")
        /** 深色模式背景壁纸路径的存储键 */
        val KEY_BACKGROUND_IMAGE_PATH_DARK = stringPreferencesKey("background_image_path_dark")
        /** 壁纸是否跟随深浅色模式独立切换的存储键 */
        val KEY_WALLPAPER_FOLLOW_DARK_MODE = booleanPreferencesKey("wallpaper_follow_dark_mode")
        /** 壁纸模糊半径存储键 */
        val KEY_WALLPAPER_BLUR_DP = floatPreferencesKey("wallpaper_blur_dp")
        /** 壁纸压暗遮罩浓度存储键 */
        val KEY_WALLPAPER_DIM_ALPHA = floatPreferencesKey("wallpaper_dim_alpha")
        /** 卡片液态玻璃质感开关存储键（默认关闭） */
        val KEY_WALLPAPER_GLASS_ENABLED = booleanPreferencesKey("wallpaper_liquid_glass_enabled")
        /** 卡片柔雾防花底浓度存储键 */
        val KEY_WALLPAPER_CARD_FROST = floatPreferencesKey("wallpaper_card_frost")
        /** 最近三张壁纸 JSON 列表存储键 */
        val KEY_RECENT_WALLPAPERS_JSON = stringPreferencesKey("recent_wallpapers_json")
        /** 壁纸是否同步到校园页存储键 */
        val KEY_WALLPAPER_SYNC_CAMPUS = booleanPreferencesKey("wallpaper_sync_campus")
        /** 壁纸是否同步到设置页存储键 */
        val KEY_WALLPAPER_SYNC_SETTINGS = booleanPreferencesKey("wallpaper_sync_settings")
        /** 开发者模式总开关的存储键 */
        val KEY_DEVELOPER_MODE_ENABLED = booleanPreferencesKey("developer_mode_enabled")
        /** 软件全局界面字号倍率的存储键 */
        val KEY_APP_UI_FONT_SCALE = floatPreferencesKey("app_ui_font_scale")

        /**
         * 允许备份的 DataStore 键集合白名单。
         */
        val BACKUP_KEYS: Set<Preferences.Key<*>> = setOf(
            KEY_CURRENT_COURSE_TABLE_ID,
            KEY_REMIND_BEFORE_MINUTES,
            KEY_HOLIDAYS_JSON,
            KEY_SHOW_NON_CURRENT_WEEK_COURSES,
            KEY_CUSTOM_LIGHT_PRIMARY,
            KEY_CUSTOM_DARK_PRIMARY
        )

        private val json = Json { ignoreUnknownKeys = true }

        /**
         * 从 Preferences 中解析出 AppSettingsModel
         */
        fun fromPreferences(prefs: Preferences, fallbackTableId: String): AppSettingsModel {
            val d = AppSettingsModel() // 默认值模板

            val rawHolidaysJson = prefs[KEY_HOLIDAYS_JSON]
            val parsedHolidays = if (!rawHolidaysJson.isNullOrEmpty()) {
                runCatching { json.decodeFromString<List<Holiday>>(rawHolidaysJson) }.getOrDefault(emptyList())
            } else {
                emptyList()
            }

            val rawRecentJson = prefs[KEY_RECENT_WALLPAPERS_JSON]
            val parsedRecents = if (!rawRecentJson.isNullOrEmpty()) {
                runCatching { json.decodeFromString<List<String>>(rawRecentJson) }.getOrDefault(emptyList())
            } else {
                emptyList()
            }

            return AppSettingsModel(
                currentCourseTableId = prefs[KEY_CURRENT_COURSE_TABLE_ID] ?: fallbackTableId.ifEmpty { d.currentCourseTableId },
                reminderEnabled = prefs[KEY_REMINDER_ENABLED] ?: d.reminderEnabled,
                remindBeforeMinutes = prefs[KEY_REMIND_BEFORE_MINUTES] ?: d.remindBeforeMinutes,
                holidays = parsedHolidays,
                autoModeEnabled = prefs[KEY_AUTO_MODE_ENABLED] ?: d.autoModeEnabled,
                autoControlMode = AutoControlMode.fromString(prefs[KEY_AUTO_CONTROL_MODE]),
                compatWearableSync = prefs[KEY_COMPAT_WEARABLE_SYNC] ?: d.compatWearableSync,
                showNonCurrentWeekCourses = prefs[KEY_SHOW_NON_CURRENT_WEEK_COURSES] ?: d.showNonCurrentWeekCourses,
                startScreen = prefs[KEY_START_SCREEN]?.let { StartScreen.fromString(it) } ?: d.startScreen,
                themeMode = prefs[KEY_THEME_MODE]?.let { AppThemeMode.fromString(it) } ?: d.themeMode,
                useDynamicColor = prefs[KEY_USE_DYNAMIC_COLOR] ?: d.useDynamicColor,
                customPrimaryColor = LightDarkValue(
                    light = prefs[KEY_CUSTOM_LIGHT_PRIMARY] ?: d.customPrimaryColor.light,
                    dark = prefs[KEY_CUSTOM_DARK_PRIMARY] ?: d.customPrimaryColor.dark
                ),
                backgroundImagePath = LightDarkValue(
                    light = prefs[KEY_BACKGROUND_IMAGE_PATH_LIGHT] ?: d.backgroundImagePath.light,
                    dark = prefs[KEY_BACKGROUND_IMAGE_PATH_DARK] ?: d.backgroundImagePath.dark
                ),
                wallpaperFollowDarkMode = prefs[KEY_WALLPAPER_FOLLOW_DARK_MODE] ?: d.wallpaperFollowDarkMode,
                wallpaperBlurDp = (prefs[KEY_WALLPAPER_BLUR_DP] ?: d.wallpaperBlurDp).coerceIn(0f, 28f),
                wallpaperDimAlpha = (prefs[KEY_WALLPAPER_DIM_ALPHA] ?: d.wallpaperDimAlpha).coerceIn(0f, 0.55f),
                wallpaperGlassEnabled = prefs[KEY_WALLPAPER_GLASS_ENABLED] ?: d.wallpaperGlassEnabled,
                wallpaperCardFrost = (prefs[KEY_WALLPAPER_CARD_FROST] ?: d.wallpaperCardFrost).coerceIn(0f, 0.85f),
                recentWallpapers = parsedRecents.filter { it.isNotBlank() }.distinct().take(3),
                wallpaperSyncCampus = prefs[KEY_WALLPAPER_SYNC_CAMPUS] ?: d.wallpaperSyncCampus,
                wallpaperSyncSettings = prefs[KEY_WALLPAPER_SYNC_SETTINGS] ?: d.wallpaperSyncSettings,
                developerModeEnabled = prefs[KEY_DEVELOPER_MODE_ENABLED] ?: d.developerModeEnabled,
                appUiFontScale = (prefs[KEY_APP_UI_FONT_SCALE] ?: d.appUiFontScale).coerceIn(0.85f, 1.25f),
            )
        }
    }
}