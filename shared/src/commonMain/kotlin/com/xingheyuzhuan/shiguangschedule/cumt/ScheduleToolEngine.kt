package com.xingheyuzhuan.shiguangschedule.cumt

import com.xingheyuzhuan.shiguangschedule.data.db.main.Course
import com.xingheyuzhuan.shiguangschedule.data.db.main.CourseTableConfig
import com.xingheyuzhuan.shiguangschedule.data.repository.AppSettingsRepository
import com.xingheyuzhuan.shiguangschedule.data.repository.CourseTableRepository
import com.xingheyuzhuan.shiguangschedule.data.repository.StyleSettingsRepository
import com.xingheyuzhuan.shiguangschedule.data.repository.TimeScheduleRepository
import com.xingheyuzhuan.shiguangschedule.data.sync.WidgetDataSynchronizer
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.koin.core.annotation.Single
import kotlin.math.abs
import kotlin.math.round
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

data class ScheduleToolDescriptor(
    val name: String,
    val description: String,
    val isWrite: Boolean,
    val inputSchema: JsonObject
)

data class ScheduleToolExecutionResult(
    val success: Boolean,
    val summary: String,
    val payloadJson: JsonObject
)

data class ScheduleResourceDescriptor(
    val uri: String,
    val name: String,
    val description: String,
    val mimeType: String = "application/json"
)

@OptIn(ExperimentalUuidApi::class)
@Single
class ScheduleToolEngine(
    private val courseTableRepository: CourseTableRepository,
    private val appSettingsRepository: AppSettingsRepository,
    private val timeScheduleRepository: TimeScheduleRepository,
    private val styleSettingsRepository: StyleSettingsRepository,
    private val cumtLocalRepository: CumtLocalRepository,
    private val widgetDataSynchronizer: WidgetDataSynchronizer
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = false
    }

    fun listTools(): List<ScheduleToolDescriptor> = TOOL_DESCRIPTORS

    fun listResources(): List<ScheduleResourceDescriptor> = RESOURCE_DESCRIPTORS

    fun isWriteTool(toolName: String): Boolean =
        TOOL_DESCRIPTORS.firstOrNull { it.name == toolName }?.isWrite == true

    fun toOpenAiToolsJson(): JsonArray = buildJsonArray {
        TOOL_DESCRIPTORS.forEach { tool ->
            addJsonObject {
                put("type", "function")
                putJsonObject("function") {
                    put("name", tool.name)
                    put("description", tool.description)
                    put("parameters", tool.inputSchema)
                }
            }
        }
    }

    fun toGeminiToolsJson(): JsonArray = buildJsonArray {
        addJsonObject {
            putJsonArray("functionDeclarations") {
                TOOL_DESCRIPTORS.forEach { tool ->
                    addJsonObject {
                        put("name", tool.name)
                        put("description", tool.description)
                        put("parameters", stripUnsupportedGeminiSchemaFields(tool.inputSchema))
                    }
                }
            }
        }
    }

    fun toMcpToolsJson(): JsonArray = buildJsonArray {
        TOOL_DESCRIPTORS.forEach { tool ->
            addJsonObject {
                put("name", tool.name)
                put("description", tool.description)
                put("inputSchema", tool.inputSchema)
            }
        }
    }

    fun toMcpResourcesJson(): JsonArray = buildJsonArray {
        RESOURCE_DESCRIPTORS.forEach { res ->
            addJsonObject {
                put("uri", res.uri)
                put("name", res.name)
                put("description", res.description)
                put("mimeType", res.mimeType)
            }
        }
    }

    suspend fun readResource(uri: String): Result<String> = runCatching {
        when (uri.trim()) {
            "schedule://tables" -> executeListCourseTables().payloadJson.toString()
            "schedule://current/courses" -> executeGetCourses(JsonObject(emptyMap())).payloadJson.toString()
            "cumt://exams_and_scores" -> executeGetExamsAndScores(JsonObject(emptyMap())).payloadJson.toString()
            else -> throw IllegalArgumentException("未知的资源 URI: $uri")
        }
    }

    fun buildPendingActionPreview(
        toolName: String,
        arguments: JsonObject
    ): CumtAiPendingAction {
        val actionId = "act_${Uuid.random().toString().take(8)}"
        return when (toolName) {
            "add_courses" -> {
                val courses = parseCoursesArrayArg(arguments)
                val addPrefix = arguments["addLabPrefix"]?.jsonPrimitive?.booleanOrNull == true
                val detailLines = courses.map { c ->
                    val title = if (addPrefix && !c.name.startsWith("[实验]")) "[实验] ${c.name}" else c.name
                    val dayStr = CumtParsers.formatDayOfWeekLabel(c.day)
                    val secStr = if (c.startSection == c.endSection) "第${c.startSection}节" else "第${c.startSection}-${c.endSection}节"
                    val weekStr = "第${CumtParsers.formatWeeksCompact(c.weeks)}周"
                    val loc = c.position.ifBlank { "地点待定" }
                    val teacher = if (c.teacher.isNotBlank()) " · ${c.teacher}" else ""
                    "$title（$weekStr $dayStr $secStr · $loc$teacher）"
                }
                CumtAiPendingAction(
                    id = actionId,
                    toolName = toolName,
                    title = "追加导入 ${courses.size} 门课程",
                    summary = if (courses.isEmpty()) "待解析课程参数为空" else "将追加写入课表，不会覆盖已有课程",
                    detailLines = detailLines,
                    argumentsJson = arguments.toString()
                )
            }
            "update_course" -> {
                val target = strArg(arguments, "courseName") ?: strArg(arguments, "name") ?: strArg(arguments, "courseId") ?: "指定课程"
                val changes = buildList {
                    strArg(arguments, "name")?.let { add("名称 → $it") }
                    strArg(arguments, "teacher")?.let { add("教师 → $it") }
                    strArg(arguments, "position")?.let { add("地点 → $it") }
                    intArg(arguments, "day")?.let { add("星期 → ${CumtParsers.formatDayOfWeekLabel(it)}") }
                    val s = intArg(arguments, "startSection")
                    val e = intArg(arguments, "endSection")
                    if (s != null || e != null) add("节次 → 第${s ?: "?"}-${e ?: "?"}节")
                    val weeks = parseWeeksArg(arguments["weeks"])
                    if (weeks.isNotEmpty()) add("周次 → 第${CumtParsers.formatWeeksCompact(weeks)}周")
                    strArg(arguments, "remark")?.let { add("备注 → $it") }
                }
                CumtAiPendingAction(
                    id = actionId,
                    toolName = toolName,
                    title = "修改课程：$target",
                    summary = if (changes.isEmpty()) "更新课程信息" else changes.joinToString("，"),
                    detailLines = changes,
                    argumentsJson = arguments.toString()
                )
            }
            "reschedule_course" -> {
                val target = strArg(arguments, "courseName") ?: strArg(arguments, "courseId") ?: "指定课程"
                val fromWeek = intArg(arguments, "fromWeek")
                val toWeek = intArg(arguments, "toWeek")
                val toDay = intArg(arguments, "toDay")
                val cancelOnly = arguments["cancelOnly"]?.jsonPrimitive?.booleanOrNull == true
                val detail = if (cancelOnly) {
                    "取消第 ${fromWeek ?: "?"} 周的「$target」课程（其余周次不受影响）"
                } else {
                    val toDayStr = toDay?.let { CumtParsers.formatDayOfWeekLabel(it) } ?: "原星期"
                    val s = intArg(arguments, "toStartSection")
                    val e = intArg(arguments, "toEndSection")
                    val secStr = if (s != null) "第$s-${e ?: s}节" else "原节次"
                    val pos = strArg(arguments, "toPosition")?.let { " · $it" } ?: ""
                    "第 ${fromWeek ?: "?"} 周 → 第 ${toWeek ?: fromWeek ?: "?"} 周 $toDayStr $secStr$pos"
                }
                CumtAiPendingAction(
                    id = actionId,
                    toolName = toolName,
                    title = if (cancelOnly) "单周停课：$target" else "调课：$target",
                    summary = detail,
                    detailLines = listOf(detail),
                    argumentsJson = arguments.toString()
                )
            }
            "delete_courses" -> {
                val names = stringListArg(arguments["courseNames"])
                val ids = stringListArg(arguments["courseIds"])
                val weeks = parseWeeksArg(arguments["weeks"])
                val label = when {
                    names.isNotEmpty() -> names.joinToString("、")
                    ids.isNotEmpty() -> "${ids.size} 个课程 ID"
                    else -> "指定课程"
                }
                val summary = if (weeks.isNotEmpty()) {
                    "仅移除第 ${CumtParsers.formatWeeksCompact(weeks)} 周的课程记录：$label"
                } else {
                    "从课表中删除：$label"
                }
                CumtAiPendingAction(
                    id = actionId,
                    toolName = toolName,
                    title = "删除课程",
                    summary = summary,
                    detailLines = listOf(summary),
                    argumentsJson = arguments.toString()
                )
            }
            "update_table_config" -> {
                val details = buildList {
                    strArg(arguments, "semesterStartDate")?.let { add("开学日期 → $it") }
                    intArg(arguments, "semesterTotalWeeks")?.let { add("学期总周数 → $it 周") }
                    arguments["showWeekends"]?.jsonPrimitive?.booleanOrNull?.let { add("显示周末 → ${if (it) "开启" else "关闭"}") }
                    strArg(arguments, "switchToTableId")?.let { add("切换当前激活课表 → $it") }
                }
                CumtAiPendingAction(
                    id = actionId,
                    toolName = toolName,
                    title = "更新课表配置",
                    summary = details.joinToString("，").ifBlank { "修改课表设置" },
                    detailLines = details,
                    argumentsJson = arguments.toString()
                )
            }
            else -> CumtAiPendingAction(
                id = actionId,
                toolName = toolName,
                title = "执行操作：$toolName",
                summary = arguments.toString().take(120),
                argumentsJson = arguments.toString()
            )
        }
    }

    suspend fun executeTool(
        toolName: String,
        arguments: JsonObject,
        allowWrite: Boolean = true
    ): ScheduleToolExecutionResult {
        if (isWriteTool(toolName) && !allowWrite) {
            return ScheduleToolExecutionResult(
                success = false,
                summary = "当前处于只读模式，已拦截写入工具 $toolName",
                payloadJson = buildJsonObject {
                    put("ok", false)
                    put("error", "READ_ONLY_MODE: 外部 MCP 当前未开启写入权限，请在 App 的 MCP 设置中开启「允许修改课表」。")
                }
            )
        }

        return try {
            when (toolName) {
                "list_course_tables" -> executeListCourseTables()
                "get_courses" -> executeGetCourses(arguments)
                "find_free_slots" -> executeFindFreeSlots(arguments)
                "get_exams_and_scores" -> executeGetExamsAndScores(arguments)
                "add_courses" -> executeAddCourses(arguments)
                "update_course" -> executeUpdateCourse(arguments)
                "reschedule_course" -> executeRescheduleCourse(arguments)
                "delete_courses" -> executeDeleteCourses(arguments)
                "update_table_config" -> executeUpdateTableConfig(arguments)
                else -> ScheduleToolExecutionResult(
                    success = false,
                    summary = "未知工具：$toolName",
                    payloadJson = buildJsonObject {
                        put("ok", false)
                        put("error", "Unknown tool: $toolName")
                    }
                )
            }
        } catch (e: Exception) {
            val err = e.message ?: "工具执行异常"
            ScheduleToolExecutionResult(
                success = false,
                summary = "执行 $toolName 失败：$err",
                payloadJson = buildJsonObject {
                    put("ok", false)
                    put("error", err)
                }
            )
        }
    }

    suspend fun executePendingAction(action: CumtAiPendingAction): ScheduleToolExecutionResult {
        val args = runCatching {
            json.parseToJsonElement(action.argumentsJson).jsonObject
        }.getOrDefault(JsonObject(emptyMap()))
        return executeTool(action.toolName, args, allowWrite = true)
    }

    // ==================== 只读工具实现 ====================

    private suspend fun executeListCourseTables(): ScheduleToolExecutionResult {
        val settings = appSettingsRepository.getAppSettingsOnce()
        val tables = courseTableRepository.getAllCourseTables().first()
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

        val tablesJson = buildJsonArray {
            for (table in tables) {
                val config = appSettingsRepository.getCourseConfigOnce(table.id)
                val currentWeek = if (config != null) {
                    appSettingsRepository.getWeekIndexAtDate(
                        targetDate = today,
                        startDateStr = config.semesterStartDate,
                        firstDayOfWeekInt = config.firstDayOfWeek
                    )
                } else null
                val courseCount = courseTableRepository.getCoursesWithWeeksByTableId(table.id).first().size
                val slots = timeScheduleRepository.getEffectiveTimeSlotsOnce(table.id, today)
                addJsonObject {
                    put("id", table.id)
                    put("name", table.name)
                    put("isCurrent", table.id == settings.currentCourseTableId)
                    put("courseCount", courseCount)
                    put("semesterStartDate", config?.semesterStartDate.orEmpty())
                    put("semesterTotalWeeks", config?.semesterTotalWeeks ?: 20)
                    put("showWeekends", config?.showWeekends ?: false)
                    if (currentWeek != null) put("currentWeek", currentWeek)
                    putJsonArray("timeSlots") {
                        slots.sortedBy { it.number }.forEach { slot ->
                            addJsonObject {
                                put("number", slot.number)
                                put("startTime", slot.startTime)
                                put("endTime", slot.endTime)
                            }
                        }
                    }
                }
            }
        }

        val payload = buildJsonObject {
            put("ok", true)
            put("today", today.toString())
            put("currentCourseTableId", settings.currentCourseTableId)
            put("tables", tablesJson)
        }
        return ScheduleToolExecutionResult(
            success = true,
            summary = "已读取 ${tables.size} 个课表及当前学期信息",
            payloadJson = payload
        )
    }

    private suspend fun resolveTargetTableId(arguments: JsonObject): String {
        val explicit = strArg(arguments, "tableId")
        if (!explicit.isNullOrBlank()) return explicit
        val currentId = appSettingsRepository.getAppSettingsOnce().currentCourseTableId
        if (currentId.isNotBlank()) return currentId
        val firstTable = courseTableRepository.getAllCourseTables().first().firstOrNull()
        return firstTable?.id ?: throw IllegalStateException("当前没有任何课表，请先创建一个课表")
    }

    private suspend fun executeGetCourses(arguments: JsonObject): ScheduleToolExecutionResult {
        val tableId = resolveTargetTableId(arguments)
        val tableName = courseTableRepository.getAllCourseTables().first()
            .firstOrNull { it.id == tableId }?.name ?: tableId
        val config = appSettingsRepository.getCourseConfigOnce(tableId)
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val currentWeek = if (config != null) {
            appSettingsRepository.getWeekIndexAtDate(today, config.semesterStartDate, config.firstDayOfWeek)
        } else null

        val weekFilter = intArg(arguments, "week")
        val dayFilter = intArg(arguments, "day")
        val keyword = strArg(arguments, "keyword")?.lowercase()

        val slotMap = timeScheduleRepository.getEffectiveTimeSlotsOnce(tableId, today)
            .associateBy { it.number }

        val allCourses = courseTableRepository.getCoursesWithWeeksByTableId(tableId).first()
        val filtered = allCourses.filter { cw ->
            val weekNums = cw.weeks.map { it.weekNumber }
            val matchWeek = weekFilter == null || weekFilter in weekNums
            val matchDay = dayFilter == null || cw.course.day == dayFilter
            val matchKw = keyword.isNullOrBlank() ||
                cw.course.name.lowercase().contains(keyword) ||
                cw.course.teacher.lowercase().contains(keyword) ||
                cw.course.position.lowercase().contains(keyword)
            matchWeek && matchDay && matchKw
        }.sortedWith(compareBy({ it.course.day }, { it.course.startSection ?: 0 }, { it.course.name }))

        val coursesJson = buildJsonArray {
            filtered.forEach { cw ->
                val c = cw.course
                val weeks = cw.weeks.map { it.weekNumber }.distinct().sorted()
                val timeRange = if (c.isCustomTime) {
                    "${c.customStartTime.orEmpty()}-${c.customEndTime.orEmpty()}"
                } else {
                    val sTime = c.startSection?.let { slotMap[it]?.startTime }
                    val eTime = c.endSection?.let { slotMap[it]?.endTime }
                    if (sTime != null && eTime != null) "$sTime-$eTime" else ""
                }
                addJsonObject {
                    put("id", c.id)
                    put("name", c.name)
                    put("teacher", c.teacher)
                    put("position", c.position)
                    put("day", c.day)
                    put("dayLabel", CumtParsers.formatDayOfWeekLabel(c.day))
                    if (c.startSection != null) put("startSection", c.startSection)
                    if (c.endSection != null) put("endSection", c.endSection)
                    put("isCustomTime", c.isCustomTime)
                    if (timeRange.isNotBlank()) put("timeRange", timeRange)
                    putJsonArray("weeks") { weeks.forEach { add(it) } }
                    put("weeksFormatted", CumtParsers.formatWeeksCompact(weeks))
                    if (!c.remark.isNullOrBlank()) put("remark", c.remark)
                    if (c.credit != null) put("credit", c.credit)
                }
            }
        }

        val payload = buildJsonObject {
            put("ok", true)
            put("tableId", tableId)
            put("tableName", tableName)
            put("today", today.toString())
            if (currentWeek != null) put("currentWeek", currentWeek)
            put("semesterTotalWeeks", config?.semesterTotalWeeks ?: 20)
            put("count", filtered.size)
            put("courses", coursesJson)
        }
        return ScheduleToolExecutionResult(
            success = true,
            summary = "已查询课表「$tableName」共 ${filtered.size} 条课程",
            payloadJson = payload
        )
    }

    private suspend fun executeFindFreeSlots(arguments: JsonObject): ScheduleToolExecutionResult {
        val tableId = resolveTargetTableId(arguments)
        val config = appSettingsRepository.getCourseConfigOnce(tableId)
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val currentWeek = if (config != null) {
            appSettingsRepository.getWeekIndexAtDate(today, config.semesterStartDate, config.firstDayOfWeek)
        } else null

        val targetWeek = intArg(arguments, "week") ?: currentWeek ?: 1
        val targetDay = intArg(arguments, "day")
        val slots = timeScheduleRepository.getEffectiveTimeSlotsOnce(tableId, today).sortedBy { it.number }
        val maxSection = (slots.maxOfOrNull { it.number } ?: 12).coerceAtLeast(10)
        val slotMap = slots.associateBy { it.number }

        val allCourses = courseTableRepository.getCoursesWithWeeksByTableId(tableId).first()
            .filter { cw -> cw.weeks.any { it.weekNumber == targetWeek } }

        val daysToCheck = if (targetDay != null && targetDay in 1..7) listOf(targetDay) else (1..7).toList()

        val freeByDay = buildJsonArray {
            for (day in daysToCheck) {
                val busy = BooleanArray(maxSection + 1)
                allCourses.filter { it.course.day == day }.forEach { cw ->
                    val s = cw.course.startSection
                    val e = cw.course.endSection
                    if (s != null && e != null) {
                        for (sec in s.coerceIn(1, maxSection)..e.coerceIn(1, maxSection)) {
                            busy[sec] = true
                        }
                    }
                }
                val freeRanges = mutableListOf<Pair<Int, Int>>()
                var runStart = -1
                for (sec in 1..maxSection) {
                    if (!busy[sec]) {
                        if (runStart == -1) runStart = sec
                    } else if (runStart != -1) {
                        freeRanges.add(runStart to (sec - 1))
                        runStart = -1
                    }
                }
                if (runStart != -1) freeRanges.add(runStart to maxSection)

                addJsonObject {
                    put("day", day)
                    put("dayLabel", CumtParsers.formatDayOfWeekLabel(day))
                    putJsonArray("freeRanges") {
                        freeRanges.forEach { (s, e) ->
                            val startT = slotMap[s]?.startTime.orEmpty()
                            val endT = slotMap[e]?.endTime.orEmpty()
                            addJsonObject {
                                put("startSection", s)
                                put("endSection", e)
                                put("label", if (s == e) "第${s}节" else "第${s}-${e}节")
                                if (startT.isNotBlank() && endT.isNotBlank()) {
                                    put("timeRange", "$startT-$endT")
                                }
                            }
                        }
                    }
                }
            }
        }

        val payload = buildJsonObject {
            put("ok", true)
            put("tableId", tableId)
            put("week", targetWeek)
            put("maxSection", maxSection)
            put("freeByDay", freeByDay)
        }
        return ScheduleToolExecutionResult(
            success = true,
            summary = "已计算第 $targetWeek 周空闲节次",
            payloadJson = payload
        )
    }

    private suspend fun executeGetExamsAndScores(arguments: JsonObject): ScheduleToolExecutionResult {
        val includeScores = arguments["includeScores"]?.jsonPrimitive?.booleanOrNull ?: true
        val includeExams = arguments["includeExams"]?.jsonPrimitive?.booleanOrNull ?: true
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

        val exams = if (includeExams) cumtLocalRepository.examsFlow.first() else emptyList()
        val scores = if (includeScores) cumtLocalRepository.scoresFlow.first() else emptyList()
        val mappings = if (includeScores) cumtLocalRepository.scoreMappingsFlow.first() else emptyList()
        val rate = if (includeScores) cumtLocalRepository.scoreRateFlow.first() else 1.0
        val summary = if (includeScores) CumtParsers.calculateScoreSummary(scores, rate, mappings) else null

        val payload = buildJsonObject {
            put("ok", true)
            put("today", today.toString())
            if (includeExams) {
                putJsonArray("exams") {
                    exams.forEach { ex ->
                        val daysLeft = runCatching {
                            today.daysUntil(LocalDate(ex.year, ex.month, ex.day))
                        }.getOrNull()
                        addJsonObject {
                            put("courseName", ex.courseName)
                            put("date", "${ex.year}-${ex.month.toString().padStart(2, '0')}-${ex.day.toString().padStart(2, '0')}")
                            if (daysLeft != null) put("daysLeft", daysLeft)
                            put("dateTimeRaw", ex.dateTimeRaw)
                            put("location", ex.location)
                            put("seatNumber", ex.seatNumber)
                            put("syncedToSchedule", ex.syncedToSchedule)
                        }
                    }
                }
            }
            if (includeScores && summary != null) {
                putJsonObject("scoreSummary") {
                    put("weightedGpa", round(summary.weightedGpa * 1000.0) / 1000.0)
                    put("weightedScore", round(summary.weightedScore * 100.0) / 100.0)
                    put("totalCredits", round(summary.totalCredits * 100.0) / 100.0)
                    put("includedCount", summary.includedCount)
                }
                putJsonArray("scores") {
                    scores.take(80).forEach { sc ->
                        addJsonObject {
                            put("courseName", sc.courseName)
                            put("credit", sc.credit)
                            put("scoreRaw", sc.scoreRaw)
                            if (sc.gpa != null) put("gpa", sc.gpa)
                            put("academicYear", sc.academicYear)
                            put("semester", sc.semester)
                            put("examType", sc.examType)
                            put("isIncluded", sc.isIncluded)
                        }
                    }
                }
            }
        }
        return ScheduleToolExecutionResult(
            success = true,
            summary = "已读取考试(${exams.size}条)与成绩(${scores.size}门)信息",
            payloadJson = payload
        )
    }

    // ==================== 写入工具实现 ====================

    private suspend fun executeAddCourses(arguments: JsonObject): ScheduleToolExecutionResult {
        val tableId = resolveTargetTableId(arguments)
        val coursesToAdd = parseCoursesArrayArg(arguments)
        if (coursesToAdd.isEmpty()) {
            return ScheduleToolExecutionResult(
                success = false,
                summary = "未提供有效的课程参数（需包含名称、星期、起止节次与周次）",
                payloadJson = buildJsonObject {
                    put("ok", false)
                    put("error", "No valid courses provided")
                }
            )
        }

        val addLabPrefix = arguments["addLabPrefix"]?.jsonPrimitive?.booleanOrNull == true
        val existingCourses = courseTableRepository.getCoursesWithWeeksByTableId(tableId).first()
        val colorCount = styleSettingsRepository.styleFlow.first().courseColorMaps.size.coerceAtLeast(1)
        val existingColors = existingCourses.associate { it.course.name to it.course.colorInt }
        val newColors = mutableMapOf<String, Int>()

        var maxWeekNeeded = 0
        var hasWeekend = false
        val insertedIds = mutableListOf<String>()

        for (item in coursesToAdd) {
            val validWeeks = item.weeks.filter { it in 1..30 }.distinct().sorted()
            if (validWeeks.isEmpty()) continue
            val finalName = if (addLabPrefix && !item.name.startsWith("[实验]")) "[实验] ${item.name}" else item.name
            val colorInt = existingColors[finalName] ?: newColors.getOrPut(finalName) {
                abs(finalName.hashCode()) % colorCount
            }
            val newId = Uuid.random().toString()
            val course = Course(
                id = newId,
                courseTableId = tableId,
                name = finalName,
                teacher = item.teacher,
                position = item.position,
                day = item.day.coerceIn(1, 7),
                startSection = item.startSection.coerceIn(1, 20),
                endSection = item.endSection.coerceIn(item.startSection.coerceIn(1, 20), 20),
                isCustomTime = false,
                customStartTime = null,
                customEndTime = null,
                colorInt = colorInt,
                remark = item.remark.ifBlank { "AI 助手导入" },
                credit = null
            )
            courseTableRepository.upsertCourse(course, validWeeks)
            insertedIds.add(newId)
            maxWeekNeeded = maxOf(maxWeekNeeded, validWeeks.max())
            if (course.day >= 6) hasWeekend = true
        }

        val adjustments = autoAdjustTableConfigIfNeeded(tableId, hasWeekend, maxWeekNeeded)
        widgetDataSynchronizer.syncNow()

        val extraNote = if (adjustments.isNotEmpty()) "（${adjustments.joinToString("，")}）" else ""
        val summary = "已追加导入 ${insertedIds.size} 条课程$extraNote"
        return ScheduleToolExecutionResult(
            success = true,
            summary = summary,
            payloadJson = buildJsonObject {
                put("ok", true)
                put("tableId", tableId)
                put("addedCount", insertedIds.size)
                putJsonArray("courseIds") { insertedIds.forEach { add(it) } }
                put("message", summary)
            }
        )
    }

    private suspend fun executeUpdateCourse(arguments: JsonObject): ScheduleToolExecutionResult {
        val tableId = resolveTargetTableId(arguments)
        val allCourses = courseTableRepository.getCoursesWithWeeksByTableId(tableId).first()
        val courseId = strArg(arguments, "courseId")
        val matchName = strArg(arguments, "courseName")
        val target = when {
            !courseId.isNullOrBlank() -> allCourses.firstOrNull { it.course.id == courseId }
            !matchName.isNullOrBlank() -> allCourses.firstOrNull { it.course.name.equals(matchName, ignoreCase = true) }
                ?: allCourses.firstOrNull { it.course.name.contains(matchName, ignoreCase = true) }
            else -> null
        } ?: return ScheduleToolExecutionResult(
            success = false,
            summary = "未找到待修改的目标课程",
            payloadJson = buildJsonObject {
                put("ok", false)
                put("error", "Course not found; please call get_courses first to get exact courseId.")
            }
        )

        val old = target.course
        val newDay = intArg(arguments, "day")?.coerceIn(1, 7) ?: old.day
        val newStart = intArg(arguments, "startSection")?.coerceIn(1, 20) ?: old.startSection
        val newEnd = intArg(arguments, "endSection")?.coerceIn(newStart ?: 1, 20) ?: old.endSection
        val parsedWeeks = parseWeeksArg(arguments["weeks"])
        val newWeeks = if (parsedWeeks.isNotEmpty()) parsedWeeks else target.weeks.map { it.weekNumber }.distinct().sorted()

        val updated = old.copy(
            name = strArg(arguments, "name")?.ifBlank { old.name } ?: old.name,
            teacher = strArg(arguments, "teacher") ?: old.teacher,
            position = strArg(arguments, "position") ?: old.position,
            day = newDay,
            startSection = newStart,
            endSection = newEnd,
            remark = strArg(arguments, "remark") ?: old.remark
        )
        courseTableRepository.upsertCourse(updated, newWeeks)
        autoAdjustTableConfigIfNeeded(tableId, updated.day >= 6, newWeeks.maxOrNull() ?: 0)
        widgetDataSynchronizer.syncNow()

        val summary = "已更新课程「${updated.name}」（第${CumtParsers.formatWeeksCompact(newWeeks)}周 ${CumtParsers.formatDayOfWeekLabel(updated.day)} 第${updated.startSection}-${updated.endSection}节）"
        return ScheduleToolExecutionResult(
            success = true,
            summary = summary,
            payloadJson = buildJsonObject {
                put("ok", true)
                put("courseId", updated.id)
                put("message", summary)
            }
        )
    }

    private suspend fun executeRescheduleCourse(arguments: JsonObject): ScheduleToolExecutionResult {
        val tableId = resolveTargetTableId(arguments)
        val fromWeek = intArg(arguments, "fromWeek")
            ?: return ScheduleToolExecutionResult(
                success = false,
                summary = "调课失败：缺少原周次 fromWeek",
                payloadJson = buildJsonObject { put("ok", false); put("error", "Missing fromWeek") }
            )
        val fromDay = intArg(arguments, "fromDay")
        val courseId = strArg(arguments, "courseId")
        val courseName = strArg(arguments, "courseName")
        val cancelOnly = arguments["cancelOnly"]?.jsonPrimitive?.booleanOrNull == true

        val allCourses = courseTableRepository.getCoursesWithWeeksByTableId(tableId).first()
        val target = allCourses.firstOrNull { cw ->
            val matchIdOrName = when {
                !courseId.isNullOrBlank() -> cw.course.id == courseId
                !courseName.isNullOrBlank() -> cw.course.name.contains(courseName, ignoreCase = true)
                else -> false
            }
            val matchWeek = cw.weeks.any { it.weekNumber == fromWeek }
            val matchDay = fromDay == null || cw.course.day == fromDay
            matchIdOrName && matchWeek && matchDay
        } ?: return ScheduleToolExecutionResult(
            success = false,
            summary = "未在第 $fromWeek 周找到匹配的课程",
            payloadJson = buildJsonObject {
                put("ok", false)
                put("error", "No matching course found in week $fromWeek")
            }
        )

        val oldWeeks = target.weeks.map { it.weekNumber }.distinct().sorted()
        val remainingWeeks = oldWeeks.filter { it != fromWeek }

        if (cancelOnly) {
            if (remainingWeeks.isEmpty()) {
                courseTableRepository.deleteCourse(target.course)
            } else {
                courseTableRepository.upsertCourse(target.course, remainingWeeks)
            }
            widgetDataSynchronizer.syncNow()
            val summary = "已取消第 $fromWeek 周的「${target.course.name}」"
            return ScheduleToolExecutionResult(
                success = true,
                summary = summary,
                payloadJson = buildJsonObject { put("ok", true); put("message", summary) }
            )
        }

        val toWeek = (intArg(arguments, "toWeek") ?: fromWeek).coerceIn(1, 30)
        val toDay = (intArg(arguments, "toDay") ?: target.course.day).coerceIn(1, 7)
        val toStart = intArg(arguments, "toStartSection")?.coerceIn(1, 20) ?: target.course.startSection
        val toEnd = intArg(arguments, "toEndSection")?.coerceIn(toStart ?: 1, 20) ?: target.course.endSection
        val toPos = strArg(arguments, "toPosition") ?: target.course.position

        if (remainingWeeks.isEmpty()) {
            // 原来只有这一周，直接原地修改
            val moved = target.course.copy(
                day = toDay,
                startSection = toStart,
                endSection = toEnd,
                position = toPos
            )
            courseTableRepository.upsertCourse(moved, listOf(toWeek))
        } else {
            // 原来有多周：从原课程移除 fromWeek，并克隆一条新单周记录
            courseTableRepository.upsertCourse(target.course, remainingWeeks)
            val cloned = target.course.copy(
                id = Uuid.random().toString(),
                day = toDay,
                startSection = toStart,
                endSection = toEnd,
                position = toPos,
                remark = listOfNotNull(target.course.remark?.takeIf { it.isNotBlank() }, "第${fromWeek}周调课")
                    .joinToString(" · ")
            )
            courseTableRepository.upsertCourse(cloned, listOf(toWeek))
        }

        autoAdjustTableConfigIfNeeded(tableId, toDay >= 6, toWeek)
        widgetDataSynchronizer.syncNow()

        val summary = "已将「${target.course.name}」从第${fromWeek}周调至第${toWeek}周 ${CumtParsers.formatDayOfWeekLabel(toDay)} 第${toStart}-${toEnd}节"
        return ScheduleToolExecutionResult(
            success = true,
            summary = summary,
            payloadJson = buildJsonObject { put("ok", true); put("message", summary) }
        )
    }

    private suspend fun executeDeleteCourses(arguments: JsonObject): ScheduleToolExecutionResult {
        val tableId = resolveTargetTableId(arguments)
        val ids = stringListArg(arguments["courseIds"])
        val names = stringListArg(arguments["courseNames"])
        val weeksFilter = parseWeeksArg(arguments["weeks"])

        if (ids.isEmpty() && names.isEmpty()) {
            return ScheduleToolExecutionResult(
                success = false,
                summary = "请提供要删除的 courseIds 或 courseNames",
                payloadJson = buildJsonObject { put("ok", false); put("error", "Missing courseIds or courseNames") }
            )
        }

        val allCourses = courseTableRepository.getCoursesWithWeeksByTableId(tableId).first()
        val matched = allCourses.filter { cw ->
            cw.course.id in ids || names.any { n -> cw.course.name.equals(n, ignoreCase = true) }
        }
        if (matched.isEmpty()) {
            return ScheduleToolExecutionResult(
                success = false,
                summary = "未找到可删除的匹配课程",
                payloadJson = buildJsonObject { put("ok", false); put("error", "No matching courses found") }
            )
        }

        var affected = 0
        if (weeksFilter.isEmpty()) {
            courseTableRepository.deleteCoursesByIds(matched.map { it.course.id })
            affected = matched.size
        } else {
            val removeSet = weeksFilter.toSet()
            for (cw in matched) {
                val kept = cw.weeks.map { it.weekNumber }.filterNot { it in removeSet }.distinct().sorted()
                if (kept.isEmpty()) {
                    courseTableRepository.deleteCourse(cw.course)
                } else {
                    courseTableRepository.upsertCourse(cw.course, kept)
                }
                affected++
            }
        }
        widgetDataSynchronizer.syncNow()

        val summary = if (weeksFilter.isEmpty()) {
            "已删除 $affected 条课程记录"
        } else {
            "已为 $affected 条课程移除第 ${CumtParsers.formatWeeksCompact(weeksFilter)} 周安排"
        }
        return ScheduleToolExecutionResult(
            success = true,
            summary = summary,
            payloadJson = buildJsonObject {
                put("ok", true)
                put("affectedCount", affected)
                put("message", summary)
            }
        )
    }

    private suspend fun executeUpdateTableConfig(arguments: JsonObject): ScheduleToolExecutionResult {
        val switchTableId = strArg(arguments, "switchToTableId")
        val targetTableId = switchTableId ?: resolveTargetTableId(arguments)
        val notes = mutableListOf<String>()

        if (!switchTableId.isNullOrBlank()) {
            val tables = courseTableRepository.getAllCourseTables().first()
            val exists = tables.any { it.id == switchTableId }
            if (!exists) {
                return ScheduleToolExecutionResult(
                    success = false,
                    summary = "目标课表 ID 不存在：$switchTableId",
                    payloadJson = buildJsonObject { put("ok", false); put("error", "Table not found: $switchTableId") }
                )
            }
            val currentSettings = appSettingsRepository.getAppSettingsOnce()
            appSettingsRepository.insertOrUpdateAppSettings(currentSettings.copy(currentCourseTableId = switchTableId))
            notes.add("已切换当前激活课表")
        }

        val currentConfig = appSettingsRepository.getCourseConfigOnce(targetTableId)
            ?: CourseTableConfig(courseTableId = targetTableId)

        val newStartDate = strArg(arguments, "semesterStartDate")
        val newTotalWeeks = intArg(arguments, "semesterTotalWeeks")?.coerceIn(1, 30)
        val newShowWeekends = arguments["showWeekends"]?.jsonPrimitive?.booleanOrNull

        if (newStartDate != null || newTotalWeeks != null || newShowWeekends != null) {
            appSettingsRepository.insertOrUpdateCourseConfig(
                currentConfig.copy(
                    semesterStartDate = newStartDate ?: currentConfig.semesterStartDate,
                    semesterTotalWeeks = newTotalWeeks ?: currentConfig.semesterTotalWeeks,
                    showWeekends = newShowWeekends ?: currentConfig.showWeekends
                )
            )
            if (newStartDate != null) notes.add("开学日期设为 $newStartDate")
            if (newTotalWeeks != null) notes.add("总周数设为 $newTotalWeeks 周")
            if (newShowWeekends != null) notes.add("显示周末已${if (newShowWeekends) "开启" else "关闭"}")
        }

        widgetDataSynchronizer.syncNow()
        val summary = notes.joinToString("，").ifBlank { "课表配置无变更" }
        return ScheduleToolExecutionResult(
            success = true,
            summary = summary,
            payloadJson = buildJsonObject {
                put("ok", true)
                put("tableId", targetTableId)
                put("message", summary)
            }
        )
    }

    private suspend fun autoAdjustTableConfigIfNeeded(
        tableId: String,
        hasWeekend: Boolean,
        maxWeekNeeded: Int
    ): List<String> {
        val config = appSettingsRepository.getCourseConfigOnce(tableId) ?: return emptyList()
        val needWeekends = hasWeekend && !config.showWeekends
        val needExpand = maxWeekNeeded > config.semesterTotalWeeks
        if (!needWeekends && !needExpand) return emptyList()
        appSettingsRepository.insertOrUpdateCourseConfig(
            config.copy(
                showWeekends = config.showWeekends || needWeekends,
                semesterTotalWeeks = if (needExpand) maxWeekNeeded else config.semesterTotalWeeks
            )
        )
        return buildList {
            if (needWeekends) add("已自动开启周末显示")
            if (needExpand) add("总周数已扩展至 $maxWeekNeeded 周")
        }
    }

    // ==================== 参数解析辅助 ====================

    private fun parseCoursesArrayArg(arguments: JsonObject): List<CumtAiRecognizedCourse> {
        val rawCourses = arguments["courses"]
        if (rawCourses is JsonArray) {
            return CumtParsers.parseAiRecognizedCoursesJson(rawCourses.toString())
        }
        // 兼容模型把单门课直接扁平传在根对象上
        if (arguments.containsKey("name") || arguments.containsKey("courseName")) {
            return CumtParsers.parseAiRecognizedCoursesJson(buildJsonArray { add(arguments) }.toString())
        }
        return emptyList()
    }

    private fun parseWeeksArg(element: kotlinx.serialization.json.JsonElement?): List<Int> {
        return when (element) {
            is JsonArray -> element.mapNotNull { it.jsonPrimitive.intOrNull ?: it.jsonPrimitive.contentOrNull?.toIntOrNull() }
                .filter { it in 1..30 }.distinct().sorted()
            is JsonPrimitive -> CumtParsers.parseManualWeeksInput(element.contentOrNull.orEmpty())
            else -> emptyList()
        }
    }

    private fun stringListArg(element: kotlinx.serialization.json.JsonElement?): List<String> {
        return when (element) {
            is JsonArray -> element.mapNotNull { it.jsonPrimitive.contentOrNull?.trim()?.takeIf(String::isNotBlank) }
            is JsonPrimitive -> element.contentOrNull?.trim()?.takeIf(String::isNotBlank)?.let { listOf(it) } ?: emptyList()
            else -> emptyList()
        }
    }

    private fun strArg(obj: JsonObject, key: String): String? =
        obj[key]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }

    private fun intArg(obj: JsonObject, key: String): Int? =
        obj[key]?.jsonPrimitive?.let { prim ->
            prim.intOrNull ?: prim.contentOrNull?.trim()?.toIntOrNull()
        }

    private fun stripUnsupportedGeminiSchemaFields(obj: JsonObject): JsonObject {
        return buildJsonObject {
            for ((k, v) in obj) {
                if (k == "additionalProperties" || k == "default") continue
                when (v) {
                    is JsonObject -> put(k, stripUnsupportedGeminiSchemaFields(v))
                    is JsonArray -> putJsonArray(k) {
                        v.forEach { elem ->
                            if (elem is JsonObject) add(stripUnsupportedGeminiSchemaFields(elem)) else add(elem)
                        }
                    }
                    else -> put(k, v)
                }
            }
        }
    }

    companion object {
        val RESOURCE_DESCRIPTORS: List<ScheduleResourceDescriptor> = listOf(
            ScheduleResourceDescriptor(
                uri = "schedule://tables",
                name = "Course Tables & Settings",
                description = "所有课表列表、当前激活课表 ID、开学日期、当前周次与作息时间表"
            ),
            ScheduleResourceDescriptor(
                uri = "schedule://current/courses",
                name = "Current Schedule Courses",
                description = "当前激活课表的全部课程与周次安排 JSON"
            ),
            ScheduleResourceDescriptor(
                uri = "cumt://exams_and_scores",
                name = "CUMT Exams & Scores",
                description = "本地已同步的矿大考试倒计时、成绩列表与加权绩点摘要"
            )
        )

        val TOOL_DESCRIPTORS: List<ScheduleToolDescriptor> = listOf(
            ScheduleToolDescriptor(
                name = "list_course_tables",
                description = "获取所有课表列表、当前激活课表、开学日期、当前教学周、总周数以及每日节次时间表。",
                isWrite = false,
                inputSchema = buildJsonObject {
                    put("type", "object")
                    putJsonObject("properties") {}
                }
            ),
            ScheduleToolDescriptor(
                name = "get_courses",
                description = "查询课表中的课程列表，可按周次(week)、星期(day 1-7)或课程/教师/地点关键词(keyword)筛选。",
                isWrite = false,
                inputSchema = buildJsonObject {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("tableId") {
                            put("type", "string")
                            put("description", "可选，目标课表 ID，不填默认当前激活课表")
                        }
                        putJsonObject("week") {
                            put("type", "integer")
                            put("description", "可选，按指定周次筛选（1~30）")
                        }
                        putJsonObject("day") {
                            put("type", "integer")
                            put("description", "可选，按星期几筛选（1=周一 ... 6=周六, 7=周日）")
                        }
                        putJsonObject("keyword") {
                            put("type", "string")
                            put("description", "可选，按课程名、教师名或教室模糊搜索")
                        }
                    }
                }
            ),
            ScheduleToolDescriptor(
                name = "find_free_slots",
                description = "计算指定教学周内某一天或全周 7 天的空闲节次时间段，方便安排实验课或约讨论。",
                isWrite = false,
                inputSchema = buildJsonObject {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("tableId") { put("type", "string"); put("description", "可选，课表 ID") }
                        putJsonObject("week") { put("type", "integer"); put("description", "目标周次（不填默认当前周）") }
                        putJsonObject("day") { put("type", "integer"); put("description", "可选，星期 1~7，不填返回全周 7 天") }
                    }
                }
            ),
            ScheduleToolDescriptor(
                name = "get_exams_and_scores",
                description = "获取本地已同步的矿大考试倒计时安排、成绩单与加权均分/绩点汇总。",
                isWrite = false,
                inputSchema = buildJsonObject {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("includeExams") { put("type", "boolean"); put("description", "是否包含考试列表，默认 true") }
                        putJsonObject("includeScores") { put("type", "boolean"); put("description", "是否包含成绩与绩点，默认 true") }
                    }
                }
            ),
            ScheduleToolDescriptor(
                name = "add_courses",
                description = "向课表中单条或批量追加新课程（如文字叙述的新增课程、实验安排表识别出的实验课），不会覆盖原有课程。",
                isWrite = true,
                inputSchema = buildJsonObject {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("tableId") { put("type", "string"); put("description", "可选，目标课表 ID，默认当前课表") }
                        putJsonObject("addLabPrefix") { put("type", "boolean"); put("description", "是否自动在课名前加 [实验] 标记") }
                        putJsonObject("courses") {
                            put("type", "array")
                            put("description", "要追加的课程列表")
                            putJsonObject("items") {
                                put("type", "object")
                                putJsonObject("properties") {
                                    putJsonObject("name") { put("type", "string"); put("description", "课程或实验项目名称") }
                                    putJsonObject("teacher") { put("type", "string"); put("description", "教师姓名，可为空") }
                                    putJsonObject("position") { put("type", "string"); put("description", "教室或实验室地点，如 信控-A205") }
                                    putJsonObject("day") { put("type", "integer"); put("description", "星期几：1=周一 ... 6=周六, 7=周日") }
                                    putJsonObject("startSection") { put("type", "integer"); put("description", "开始节次，如 1") }
                                    putJsonObject("endSection") { put("type", "integer"); put("description", "结束节次，如 2 或 4") }
                                    putJsonObject("weeks") {
                                        put("type", "array")
                                        putJsonObject("items") { put("type", "integer") }
                                        put("description", "上课周次整数数组，例如 [11] 或 [11, 12]")
                                    }
                                    putJsonObject("remark") { put("type", "string"); put("description", "可选备注（如第1次、第1批、2学时）") }
                                }
                                putJsonArray("required") {
                                    add("name"); add("day"); add("startSection"); add("endSection"); add("weeks")
                                }
                            }
                        }
                    }
                    putJsonArray("required") { add("courses") }
                }
            ),
            ScheduleToolDescriptor(
                name = "update_course",
                description = "修改已有课程的名称、教师、地点、星期、起止节次、周次或备注。建议先调用 get_courses 获取 courseId。",
                isWrite = true,
                inputSchema = buildJsonObject {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("tableId") { put("type", "string") }
                        putJsonObject("courseId") { put("type", "string"); put("description", "待修改课程的 ID") }
                        putJsonObject("courseName") { put("type", "string"); put("description", "当未知 courseId 时用于匹配原课程名") }
                        putJsonObject("name") { put("type", "string"); put("description", "新课程名") }
                        putJsonObject("teacher") { put("type", "string"); put("description", "新教师名") }
                        putJsonObject("position") { put("type", "string"); put("description", "新上课地点") }
                        putJsonObject("day") { put("type", "integer"); put("description", "新星期 1~7") }
                        putJsonObject("startSection") { put("type", "integer"); put("description", "新开始节次") }
                        putJsonObject("endSection") { put("type", "integer"); put("description", "新结束节次") }
                        putJsonObject("weeks") {
                            put("type", "array")
                            putJsonObject("items") { put("type", "integer") }
                            put("description", "新的完整周次数组")
                        }
                        putJsonObject("remark") { put("type", "string"); put("description", "新备注") }
                    }
                }
            ),
            ScheduleToolDescriptor(
                name = "reschedule_course",
                description = "对某门课程执行单周调课或单周停课（自动从多周课程中拆分出该周，不影响其他周次）。",
                isWrite = true,
                inputSchema = buildJsonObject {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("tableId") { put("type", "string") }
                        putJsonObject("courseId") { put("type", "string"); put("description", "课程 ID（推荐）") }
                        putJsonObject("courseName") { put("type", "string"); put("description", "课程名称关键词") }
                        putJsonObject("fromWeek") { put("type", "integer"); put("description", "原上课周次（必填）") }
                        putJsonObject("fromDay") { put("type", "integer"); put("description", "可选，原星期 1~7") }
                        putJsonObject("cancelOnly") { put("type", "boolean"); put("description", "若为 true 则仅取消该周课程（停课），不移动到新时间") }
                        putJsonObject("toWeek") { put("type", "integer"); put("description", "调课后的目标周次") }
                        putJsonObject("toDay") { put("type", "integer"); put("description", "调课后的目标星期 1~7") }
                        putJsonObject("toStartSection") { put("type", "integer"); put("description", "调课后的开始节次") }
                        putJsonObject("toEndSection") { put("type", "integer"); put("description", "调课后的结束节次") }
                        putJsonObject("toPosition") { put("type", "string"); put("description", "调课后的新教室") }
                    }
                    putJsonArray("required") { add("fromWeek") }
                }
            ),
            ScheduleToolDescriptor(
                name = "delete_courses",
                description = "删除指定 courseIds 或 courseNames 对应的课程；若指定 weeks，则仅移除这些周次。",
                isWrite = true,
                inputSchema = buildJsonObject {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("tableId") { put("type", "string") }
                        putJsonObject("courseIds") {
                            put("type", "array")
                            putJsonObject("items") { put("type", "string") }
                            put("description", "要删除的课程 ID 列表")
                        }
                        putJsonObject("courseNames") {
                            put("type", "array")
                            putJsonObject("items") { put("type", "string") }
                            put("description", "要删除的课程名称列表")
                        }
                        putJsonObject("weeks") {
                            put("type", "array")
                            putJsonObject("items") { put("type", "integer") }
                            put("description", "可选，仅移除指定周次而不删除整门课")
                        }
                    }
                }
            ),
            ScheduleToolDescriptor(
                name = "update_table_config",
                description = "更新课表配置（开学日期 YYYY-MM-DD、总周数、显示周末开关）或切换当前激活课表。",
                isWrite = true,
                inputSchema = buildJsonObject {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("tableId") { put("type", "string") }
                        putJsonObject("switchToTableId") { put("type", "string"); put("description", "切换到该课表作为当前激活课表") }
                        putJsonObject("semesterStartDate") { put("type", "string"); put("description", "开学日期，格式 YYYY-MM-DD") }
                        putJsonObject("semesterTotalWeeks") { put("type", "integer"); put("description", "总周数 1~30") }
                        putJsonObject("showWeekends") { put("type", "boolean"); put("description", "是否显示周六周日") }
                    }
                }
            )
        )
    }
}
