package com.xingheyuzhuan.shiguangschedule.ui.schoolselection.web

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xingheyuzhuan.shiguangschedule.cumt.CumtExamScheduleSynchronizer
import com.xingheyuzhuan.shiguangschedule.cumt.CumtLocalRepository
import com.xingheyuzhuan.shiguangschedule.cumt.CumtParsers
import com.xingheyuzhuan.shiguangschedule.cumt.CumtWebScripts
import com.xingheyuzhuan.shiguangschedule.data.db.main.CourseTableConfig
import com.xingheyuzhuan.shiguangschedule.data.repository.AppSettingsRepository
import com.xingheyuzhuan.shiguangschedule.data.repository.CourseConversionRepository
import com.xingheyuzhuan.shiguangschedule.data.sync.WidgetDataSynchronizer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okio.FileSystem
import okio.Path
import org.koin.core.annotation.KoinViewModel
import org.koin.core.annotation.Named

@KoinViewModel
class WebViewModel(
    private val appSettingsRepository: AppSettingsRepository,
    private val courseConversionRepository: CourseConversionRepository,
    private val cumtLocalRepository: CumtLocalRepository,
    private val widgetDataSynchronizer: WidgetDataSynchronizer,
    private val examScheduleSynchronizer: CumtExamScheduleSynchronizer,
    @Named("FilesDir") val filesDir: Path,
    val fileSystem: FileSystem
) : ViewModel() {
    val isDeveloperModeEnabled: StateFlow<Boolean> = appSettingsRepository.getAppSettings()
        .map { it.developerModeEnabled }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    suspend fun getAutoFillScript(url: String): String? {
        val creds = cumtLocalRepository.getSavedCredentials()
        return CumtWebScripts.autoFillAndNavigateJs(
            url = url,
            username = creds?.first.orEmpty(),
            password = creds?.second.orEmpty()
        )
    }

    fun importCumtCourses(
        tableId: String,
        isYjs: Boolean,
        html: String,
        bkCourseJson: String? = null,
        fallbackXnm: String = "",
        fallbackXqm: String = "",
        onResult: (Result<Pair<Int, String?>>) -> Unit
    ) {
        viewModelScope.launch {
            val result = runCatching {
                var extractedStudentName: String? = null
                val (courses, suggestedDate) = if (isYjs) {
                    CumtParsers.parseYjsCourseHtml(html) to null
                } else {
                    val jsonRes = bkCourseJson?.takeIf { it.isNotBlank() }?.let { rawJson ->
                        CumtParsers.parseBkCourseJson(rawJson, fallbackXnm, fallbackXqm)
                    }
                    val htmlResFallback by lazy { CumtParsers.parseBkCourseHtml(html) }
                    extractedStudentName = jsonRes?.studentName ?: htmlResFallback.studentName
                    if (jsonRes != null && jsonRes.courses.isNotEmpty()) {
                        jsonRes.courses to (jsonRes.suggestedTermStartDate ?: CumtParsers.parseSuggestedTermStartDate(html))
                    } else {
                        htmlResFallback.courses to (htmlResFallback.suggestedTermStartDate ?: jsonRes?.suggestedTermStartDate)
                    }
                }
                if (!extractedStudentName.isNullOrBlank()) {
                    cumtLocalRepository.updateProfileNameIfBlank(extractedStudentName)
                }
                if (courses.isEmpty()) {
                    error("当前页面未检测到课表数据，请确认已查询并显示课表后再试")
                }
                courseConversionRepository.importCourses(tableId, courses)
                courseConversionRepository.importTimeSlots(
                    tableId = tableId,
                    timeSlots = CumtWebScripts.presetTimeSlots,
                    defaultClassDuration = 50,
                    defaultBreakDuration = 5
                )
                val settings = appSettingsRepository.getAppSettingsOnce()
                appSettingsRepository.insertOrUpdateAppSettings(
                    settings.copy(currentCourseTableId = tableId)
                )
                runCatching { examScheduleSynchronizer.syncEnabledExamsToTable(tableId) }
                widgetDataSynchronizer.syncNow()
                courses.size to suggestedDate
            }
            onResult(result)
        }
    }

    fun applyTermStartDate(tableId: String, startDate: String, onDone: () -> Unit) {
        viewModelScope.launch {
            runCatching {
                val existing = appSettingsRepository.getCourseConfigOnce(tableId)
                    ?: CourseTableConfig(courseTableId = tableId)
                appSettingsRepository.insertOrUpdateCourseConfig(
                    existing.copy(semesterStartDate = startDate)
                )
                runCatching { examScheduleSynchronizer.syncEnabledExamsToTable(tableId) }
                widgetDataSynchronizer.syncNow()
                Unit
            }
            onDone()
        }
    }
}
