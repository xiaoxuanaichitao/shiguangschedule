package com.xingheyuzhuan.shiguangschedule.cumt

import com.xingheyuzhuan.shiguangschedule.data.db.main.Course
import com.xingheyuzhuan.shiguangschedule.data.repository.AppSettingsRepository
import com.xingheyuzhuan.shiguangschedule.data.repository.CourseTableRepository
import com.xingheyuzhuan.shiguangschedule.data.repository.StyleSettingsRepository
import com.xingheyuzhuan.shiguangschedule.data.sync.WidgetDataSynchronizer
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime
import org.koin.core.annotation.Single
import kotlin.time.Clock

data class ExamSyncReport(
    val syncedCount: Int = 0,
    val skippedNoTimeCount: Int = 0,
    val skippedOutOfRangeCount: Int = 0,
    val expandedWeeksTo: Int? = null,
    val enabledWeekends: Boolean = false,
    val errorMessage: String? = null
)

@Single
class CumtExamScheduleSynchronizer(
    private val localRepo: CumtLocalRepository,
    private val courseTableRepository: CourseTableRepository,
    private val appSettingsRepository: AppSettingsRepository,
    private val styleSettingsRepository: StyleSettingsRepository,
    private val widgetDataSynchronizer: WidgetDataSynchronizer
) {

    suspend fun syncEnabledExamsToTable(tableIdOverride: String? = null): ExamSyncReport {
        val tableId = tableIdOverride?.takeIf { it.isNotBlank() }
            ?: appSettingsRepository.getAppSettingsOnce().currentCourseTableId
        if (tableId.isBlank()) {
            return ExamSyncReport(errorMessage = "请先创建或选择一个课表")
        }

        val config = appSettingsRepository.getCourseConfigOnce(tableId)
        val startDate = config?.semesterStartDate
        if (config == null || startDate.isNullOrBlank()) {
            return ExamSyncReport(errorMessage = "当前课表未设置开学日期，请先在「我的 → 开学日期」设置后再同步")
        }

        val colorSize = styleSettingsRepository.styleFlow.first().courseColorMaps.size
        val examColorIndex = (colorSize - 1).coerceAtLeast(0)

        val enabledExams = localRepo.examsFlow.first().filter { it.syncedToSchedule }
        val desiredCourseIds = mutableSetOf<String>()
        var maxNeededWeek = config.semesterTotalWeeks
        var needEnableWeekends = false
        var syncedCount = 0
        var skippedNoTime = 0
        var skippedOutOfRange = 0

        for (exam in enabledExams) {
            val hasTime = (!exam.startTime.isNullOrBlank() && !exam.endTime.isNullOrBlank()) ||
                CumtParsers.parseExamTimeRange(exam.dateTimeRaw) != null
            if (!hasTime) {
                skippedNoTime++
                continue
            }

            val slot = CumtParsers.mapExamToScheduleSlot(
                exam = exam,
                semesterStartDate = startDate,
                firstDayOfWeek = config.firstDayOfWeek,
                weekIndexCalc = appSettingsRepository::getWeekIndexAtDate
            )
            if (slot == null) {
                skippedOutOfRange++
                continue
            }

            if (slot.weekNumber > maxNeededWeek) {
                maxNeededWeek = slot.weekNumber
            }
            if (slot.dayOfWeek >= 6 && !config.showWeekends) {
                needEnableWeekends = true
            }

            val courseId = CumtParsers.buildExamCourseId(tableId, exam.id)
            desiredCourseIds.add(courseId)

            val remark = buildString {
                append("矿大考试")
                if (exam.dateTimeRaw.isNotBlank()) {
                    append(" · ")
                    append(exam.dateTimeRaw)
                }
                if (exam.seatNumber.isNotBlank()) {
                    append(" · 座位 ")
                    append(exam.seatNumber)
                }
            }

            val course = Course(
                id = courseId,
                courseTableId = tableId,
                name = "[考试] ${exam.courseName}",
                teacher = if (exam.seatNumber.isNotBlank()) "座位 ${exam.seatNumber}" else "考试",
                position = exam.location.ifBlank { "考场待定" },
                day = slot.dayOfWeek,
                startSection = null,
                endSection = null,
                isCustomTime = true,
                customStartTime = slot.startTime,
                customEndTime = slot.endTime,
                colorInt = examColorIndex,
                remark = remark,
                credit = null
            )
            courseTableRepository.upsertCourse(course, listOf(slot.weekNumber))
            syncedCount++
        }

        val expandedWeeksTo = if (maxNeededWeek > config.semesterTotalWeeks) maxNeededWeek else null
        if (expandedWeeksTo != null || needEnableWeekends) {
            appSettingsRepository.insertOrUpdateCourseConfig(
                config.copy(
                    semesterTotalWeeks = expandedWeeksTo ?: config.semesterTotalWeeks,
                    showWeekends = config.showWeekends || needEnableWeekends
                )
            )
        }

        // 清理当前课表内已取消同步或已删除的考试课程块
        val tablePrefix = "${CumtParsers.EXAM_COURSE_ID_PREFIX}${tableId}_"
        val existingCourses = courseTableRepository.getCoursesWithWeeksByTableId(tableId).first()
        val staleIds = existingCourses
            .map { it.course.id }
            .filter { it.startsWith(tablePrefix) && it !in desiredCourseIds }
        if (staleIds.isNotEmpty()) {
            courseTableRepository.deleteCoursesByIds(staleIds)
        }

        widgetDataSynchronizer.syncNow()

        return ExamSyncReport(
            syncedCount = syncedCount,
            skippedNoTimeCount = skippedNoTime,
            skippedOutOfRangeCount = skippedOutOfRange,
            expandedWeeksTo = expandedWeeksTo,
            enabledWeekends = needEnableWeekends
        )
    }

    suspend fun syncAllUpcomingToCurrentTable(): ExamSyncReport {
        val tableId = appSettingsRepository.getAppSettingsOnce().currentCourseTableId
        if (tableId.isBlank()) {
            return ExamSyncReport(errorMessage = "请先创建或选择一个课表")
        }
        val config = appSettingsRepository.getCourseConfigOnce(tableId)
        val startDate = config?.semesterStartDate
        if (config == null || startDate.isNullOrBlank()) {
            return ExamSyncReport(errorMessage = "当前课表未设置开学日期，请先在「我的 → 开学日期」设置后再同步")
        }

        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val allExams = localRepo.examsFlow.first()
        val mappableIds = HashSet<String>()
        var skippedNoTimeCount = 0
        var skippedOutOfRangeCount = 0

        for (exam in allExams) {
            val d = runCatching { LocalDate(exam.year, exam.month, exam.day) }.getOrNull() ?: continue
            if (today.daysUntil(d) < 0) continue

            val hasTime = (!exam.startTime.isNullOrBlank() && !exam.endTime.isNullOrBlank()) ||
                CumtParsers.parseExamTimeRange(exam.dateTimeRaw) != null
            if (!hasTime) {
                skippedNoTimeCount++
                continue
            }

            val slot = CumtParsers.mapExamToScheduleSlot(
                exam = exam,
                semesterStartDate = startDate,
                firstDayOfWeek = config.firstDayOfWeek,
                weekIndexCalc = appSettingsRepository::getWeekIndexAtDate
            )
            if (slot == null) {
                skippedOutOfRangeCount++
                continue
            }

            mappableIds.add(exam.id)
        }

        if (mappableIds.isEmpty()) {
            val msg = when {
                skippedOutOfRangeCount > 0 && skippedNoTimeCount == 0 ->
                    "即将到来的考试日期均不在当前课表学期范围内（需在开学后第 1~30 周）"
                skippedOutOfRangeCount > 0 && skippedNoTimeCount > 0 ->
                    "未找到可同步的待考记录（部分缺少起止时间或不在第 1~30 周范围内）"
                else ->
                    "当前没有包含有效起止时间的待考记录"
            }
            return ExamSyncReport(
                skippedNoTimeCount = skippedNoTimeCount,
                skippedOutOfRangeCount = skippedOutOfRangeCount,
                errorMessage = msg
            )
        }

        localRepo.updateAllExamsSyncFlags(mappableIds)
        val report = syncEnabledExamsToTable(tableId)
        return report.copy(
            skippedNoTimeCount = maxOf(report.skippedNoTimeCount, skippedNoTimeCount),
            skippedOutOfRangeCount = maxOf(report.skippedOutOfRangeCount, skippedOutOfRangeCount)
        )
    }

    suspend fun toggleSingleExamInSchedule(exam: CumtExamItem): ExamSyncReport {
        val targetSynced = !exam.syncedToSchedule
        if (targetSynced) {
            val hasTime = (!exam.startTime.isNullOrBlank() && !exam.endTime.isNullOrBlank()) ||
                CumtParsers.parseExamTimeRange(exam.dateTimeRaw) != null
            if (!hasTime) {
                return ExamSyncReport(errorMessage = "该倒计时未设置起止时间（如 09:00-11:00），无法插入课表")
            }

            val tableId = appSettingsRepository.getAppSettingsOnce().currentCourseTableId
            if (tableId.isBlank()) {
                return ExamSyncReport(errorMessage = "请先创建或选择一个课表")
            }
            val config = appSettingsRepository.getCourseConfigOnce(tableId)
            val startDate = config?.semesterStartDate
            if (config == null || startDate.isNullOrBlank()) {
                return ExamSyncReport(errorMessage = "当前课表未设置开学日期，请先在「我的 → 开学日期」设置后再同步")
            }
            val slot = CumtParsers.mapExamToScheduleSlot(
                exam = exam,
                semesterStartDate = startDate,
                firstDayOfWeek = config.firstDayOfWeek,
                weekIndexCalc = appSettingsRepository::getWeekIndexAtDate
            )
            if (slot == null) {
                return ExamSyncReport(errorMessage = "该考试日期不在当前课表学期范围内（需在开学后第 1~30 周），无法插入课表")
            }
        }
        localRepo.updateExamSyncFlag(exam.id, targetSynced)
        val report = syncEnabledExamsToTable()
        if (targetSynced && report.errorMessage != null) {
            localRepo.updateExamSyncFlag(exam.id, false)
        }
        return report
    }

    suspend fun removeAllSyncedExamsFromCurrentTable(): Int {
        val tableId = appSettingsRepository.getAppSettingsOnce().currentCourseTableId
        var removedCount = 0
        if (tableId.isNotBlank()) {
            val existingCourses = courseTableRepository.getCoursesWithWeeksByTableId(tableId).first()
            val examCourseIds = existingCourses
                .map { it.course.id }
                .filter { it.startsWith(CumtParsers.EXAM_COURSE_ID_PREFIX) }
            if (examCourseIds.isNotEmpty()) {
                courseTableRepository.deleteCoursesByIds(examCourseIds)
                removedCount = examCourseIds.size
            }
        }
        localRepo.clearAllExamSyncFlags()
        widgetDataSynchronizer.syncNow()
        return removedCount
    }

    suspend fun deleteExamAndCleanupSchedule(examId: String) {
        localRepo.deleteExam(examId)
        val tableId = appSettingsRepository.getAppSettingsOnce().currentCourseTableId
        if (tableId.isNotBlank()) {
            val courseId = CumtParsers.buildExamCourseId(tableId, examId)
            courseTableRepository.deleteCoursesByIds(listOf(courseId))
            widgetDataSynchronizer.syncNow()
        }
    }
}
