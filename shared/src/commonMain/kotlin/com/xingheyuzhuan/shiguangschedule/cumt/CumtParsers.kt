package com.xingheyuzhuan.shiguangschedule.cumt

import com.xingheyuzhuan.shiguangschedule.data.model.CourseImportExport.ImportCourseJsonModel
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.math.round

data class MappedExamScheduleSlot(
    val weekNumber: Int,
    val dayOfWeek: Int,
    val startTime: String,
    val endTime: String
)

data class ParsedBkScheduleResult(
    val courses: List<ImportCourseJsonModel>,
    val suggestedTermStartDate: String?
)

data class CasLoginInputs(
    val pwdEncryptSalt: String,
    val execution: String
)

private data class HtmlElementMatch(
    val openTagAttrs: String,
    val innerHtml: String
)

object CumtParsers {
    private val lenientJson = Json { ignoreUnknownKeys = true; isLenient = true }

    private val entityRegex = Regex("&(amp|lt|gt|quot|apos|nbsp|#(\\d+)|#x([0-9a-fA-F]+));")
    private val tagRegex = Regex("<[^>]+>")
    private val wsRegex = Regex("\\s+")

    fun decodeHtmlEntities(input: String): String {
        if (!input.contains('&')) return input
        return entityRegex.replace(input) { m ->
            when (m.groupValues[1]) {
                "amp" -> "&"
                "lt" -> "<"
                "gt" -> ">"
                "quot" -> "\""
                "apos" -> "'"
                "nbsp" -> " "
                else -> {
                    val dec = m.groupValues[2]
                    val hex = m.groupValues[3]
                    val codePoint = when {
                        dec.isNotEmpty() -> dec.toIntOrNull()
                        hex.isNotEmpty() -> hex.toIntOrNull(16)
                        else -> null
                    }
                    if (codePoint != null && codePoint in 1..0xFFFF) {
                        codePoint.toChar().toString()
                    } else {
                        m.value
                    }
                }
            }
        }
    }

    fun stripHtml(html: String): String {
        return decodeHtmlEntities(html.replace(tagRegex, ""))
            .replace("\u00A0", " ")
            .replace(wsRegex, " ")
            .trim()
    }

    private fun extractAttr(attrs: String, name: String): String? {
        val dq = Regex("""\b$name\s*=\s*"([^"]*)"""", RegexOption.IGNORE_CASE).find(attrs)
        if (dq != null) return decodeHtmlEntities(dq.groupValues[1])
        val sq = Regex("""\b$name\s*=\s*'([^']*)'""", RegexOption.IGNORE_CASE).find(attrs)
        if (sq != null) return decodeHtmlEntities(sq.groupValues[1])
        val unquoted = Regex("""\b$name\s*=\s*([^\s>]+)""", RegexOption.IGNORE_CASE).find(attrs)
        return unquoted?.let { decodeHtmlEntities(it.groupValues[1]) }
    }

    /**
     * 按标签嵌套深度提取同层子元素，避免表格/div 嵌套时非贪婪正则错配。
     */
    private fun extractTopLevelElements(html: String, tagName: String): List<HtmlElementMatch> {
        val openPattern = Regex("""<$tagName\b([^>]*)>""", RegexOption.IGNORE_CASE)
        val anyTagPattern = Regex("""<(/?)$tagName\b([^>]*)>""", RegexOption.IGNORE_CASE)
        val result = mutableListOf<HtmlElementMatch>()
        var searchFrom = 0
        while (searchFrom < html.length) {
            val openMatch = openPattern.find(html, searchFrom) ?: break
            val openEnd = openMatch.range.last + 1
            if (openMatch.value.endsWith("/>")) {
                result.add(HtmlElementMatch(openMatch.groupValues[1], ""))
                searchFrom = openEnd
                continue
            }
            var depth = 1
            var pos = openEnd
            var closeStart = -1
            var closeEnd = -1
            while (pos < html.length) {
                val next = anyTagPattern.find(html, pos) ?: break
                val isClosing = next.groupValues[1] == "/"
                if (!isClosing && !next.value.endsWith("/>")) {
                    depth++
                } else if (isClosing) {
                    depth--
                    if (depth == 0) {
                        closeStart = next.range.first
                        closeEnd = next.range.last + 1
                        break
                    }
                }
                pos = next.range.last + 1
            }
            if (closeStart >= 0) {
                result.add(HtmlElementMatch(openMatch.groupValues[1], html.substring(openEnd, closeStart)))
                searchFrom = closeEnd
            } else {
                break
            }
        }
        return result
    }

    // ==================== CAS 解析 ====================

    fun parseCasLoginInputs(html: String): CasLoginInputs? {
        val saltRegex = Regex("""<input\b[^>]*id\s*=\s*["']pwdEncryptSalt["'][^>]*>""", RegexOption.IGNORE_CASE)
        val saltTag = saltRegex.find(html)?.value ?: return null
        val salt = extractAttr(saltTag, "value").orEmpty()

        val execTags = Regex("""<input\b[^>]*name\s*=\s*["']execution["'][^>]*>""", RegexOption.IGNORE_CASE)
            .findAll(html)
            .mapNotNull { extractAttr(it.value, "value") }
            .filter { it.isNotBlank() }
            .toList()
        if (salt.isBlank() || execTags.isEmpty()) return null

        // CAS 账号密码表单通常对应第 3 个 #execution（若有），否则取第 1 个非空 execution
        val pwdFormMatch = Regex("""<form\b[^>]*id\s*=\s*["']pwdFromId["'][\s\S]*?</form>""", RegexOption.IGNORE_CASE)
            .find(html)?.value
        val pwdFormExec = pwdFormMatch?.let { form ->
            Regex("""<input\b[^>]*name\s*=\s*["']execution["'][^>]*>""", RegexOption.IGNORE_CASE)
                .find(form)?.let { extractAttr(it.value, "value") }
        }
        val execution = pwdFormExec?.takeIf { it.isNotBlank() }
            ?: execTags.getOrNull(2)
            ?: execTags.first()
        return CasLoginInputs(pwdEncryptSalt = salt, execution = execution)
    }

    fun parseCasConflictExecution(html: String): String? {
        val match = Regex("""<input\b[^>]*name\s*=\s*["']execution["'][^>]*>""", RegexOption.IGNORE_CASE)
            .find(html) ?: return null
        return extractAttr(match.value, "value")?.takeIf { it.isNotBlank() }
    }

    fun parseCasError(html: String): String? {
        val patterns = listOf(
            Regex("""<span\b[^>]*id\s*=\s*["']showErrorTip["'][^>]*>(.*?)</span>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)),
            Regex("""<div\b[^>]*id\s*=\s*["']msg["'][^>]*>(.*?)</div>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        )
        for (p in patterns) {
            val m = p.find(html) ?: continue
            val text = stripHtml(m.groupValues[1])
            if (text.isNotBlank()) return text
        }
        return null
    }

    // ==================== 本科课表解析（正方教务）====================

    fun parseSuggestedTermStartDate(html: String): String? {
        val h6Match = Regex("""<h6\b[^>]*class\s*=\s*["'][^"']*pull-left[^"']*["'][^>]*>(.*?)</h6>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(html)
        if (h6Match != null) {
            val text = stripHtml(h6Match.groupValues[1])
            val term = Regex("""学年第\s*(\d+)\s*学期""").find(text)?.groupValues?.get(1)
            val year = Regex("""(\d{4})\s*-""").find(text)?.groupValues?.get(1)?.toIntOrNull()
            if (term != null && year != null) {
                return when (term) {
                    "1" -> "$year-09-07"
                    "2" -> "${year + 1}-03-01"
                    else -> "${year + 1}-07-06"
                }
            }
        }
        val xnm = Regex("""data-cumt-xnm\s*=\s*["'](\d{4})["']""", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.get(1)?.toIntOrNull() ?: return null
        val xqm = Regex("""data-cumt-xqm\s*=\s*["'](\d+)["']""", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.get(1) ?: return null
        return when (xqm) {
            "3" -> "$xnm-09-07"
            "12" -> "${xnm + 1}-03-01"
            "16" -> "${xnm + 1}-07-06"
            else -> null
        }
    }

    fun parseBkCourseHtml(html: String): ParsedBkScheduleResult {
        val suggestedDate = parseSuggestedTermStartDate(html)
        val tableMatch = Regex("""<table\b[^>]*id\s*=\s*["']kbgrid_table_0["'][^>]*>""", RegexOption.IGNORE_CASE)
            .find(html)
        val searchScope = if (tableMatch != null) {
            val start = tableMatch.range.first
            val tables = extractTopLevelElements(html.substring(start), "table")
            tables.firstOrNull()?.innerHtml ?: html
        } else {
            html
        }

        val tdRegex = Regex("""<td\b([^>]*\bid\s*=\s*["']([1-7])-(\d+)["'][^>]*)>""", RegexOption.IGNORE_CASE)
        val allTds = extractAllMatchingTds(searchScope, tdRegex)

        val courses = mutableListOf<ImportCourseJsonModel>()
        val dedup = HashSet<String>()

        for ((dayOfWeek, innerHtml) in allTds) {
            val blocks = extractTopLevelElements(innerHtml, "div")
                .ifEmpty { listOf(HtmlElementMatch("", innerHtml)) }
            for (block in blocks) {
                val parsed = parseBkSingleBlock(dayOfWeek, block.innerHtml)
                for (item in parsed) {
                    val key = "${item.day}|${item.name}|${item.startSection}|${item.endSection}|${item.weeks.sorted().joinToString(",")}|${item.position}|${item.teacher}"
                    if (dedup.add(key)) {
                        courses.add(item)
                    }
                }
            }
        }
        return ParsedBkScheduleResult(courses = courses, suggestedTermStartDate = suggestedDate)
    }

    private fun extractAllMatchingTds(
        html: String,
        tdOpenRegex: Regex
    ): List<Pair<Int, String>> {
        val result = mutableListOf<Pair<Int, String>>()
        val anyTd = Regex("""<(/?)td\b([^>]*)>""", RegexOption.IGNORE_CASE)
        var searchFrom = 0
        while (searchFrom < html.length) {
            val m = tdOpenRegex.find(html, searchFrom) ?: break
            val day = m.groupValues[2].toIntOrNull() ?: 0
            val contentStart = m.range.last + 1
            var depth = 1
            var pos = contentStart
            var contentEnd = -1
            var closeEnd = -1
            while (pos < html.length) {
                val tag = anyTd.find(html, pos) ?: break
                val closing = tag.groupValues[1] == "/"
                if (!closing && !tag.value.endsWith("/>")) depth++
                else if (closing) {
                    depth--
                    if (depth == 0) {
                        contentEnd = tag.range.first
                        closeEnd = tag.range.last + 1
                        break
                    }
                }
                pos = tag.range.last + 1
            }
            if (contentEnd >= 0) {
                if (day in 1..7) {
                    result.add(day to html.substring(contentStart, contentEnd))
                }
                searchFrom = closeEnd
            } else {
                break
            }
        }
        return result
    }

    private fun parseBkSingleBlock(day: Int, blockHtml: String): List<ImportCourseJsonModel> {
        val title = (
            Regex("""<span\b[^>]*class\s*=\s*["'][^"']*\btitle\b[^"']*["'][^>]*>(.*?)</span>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                .find(blockHtml)?.groupValues?.get(1)
                ?: Regex("""<u\b[^>]*class\s*=\s*["'][^"']*\btitle\b[^"']*["'][^>]*>(.*?)</u>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                    .find(blockHtml)?.groupValues?.get(1)
            )?.let(::stripHtml).orEmpty()
        if (title.isBlank()) return emptyList()

        val pElements = extractTopLevelElements(blockHtml, "p")
        var position = ""
        var teacher = ""
        var credit: Float? = null
        var sectionWeekRaw = ""

        for (p in pElements) {
            val pHtml = p.innerHtml
            val spanTitle = Regex("""title\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE)
                .find(pHtml)?.groupValues?.get(1)?.trim() ?: continue
            val text = stripHtml(pHtml)
            when (spanTitle) {
                "上课地点" -> position = text.removePrefix("上课地点：").removePrefix("上课地点:").trim()
                "教师" -> teacher = text.removePrefix("教师：").removePrefix("教师:").trim()
                "学分" -> credit = text.replace(Regex("[^0-9.]"), "").toFloatOrNull()
                "节/周" -> sectionWeekRaw = text.replace("？", "").replace("?", "").trim()
            }
        }
        if (sectionWeekRaw.isBlank()) return emptyList()

        return expandBkCourse(
            day = day,
            name = title,
            teacher = teacher,
            position = position,
            credit = credit,
            sectionWeekRaw = sectionWeekRaw
        )
    }

    fun expandBkCourse(
        day: Int,
        name: String,
        teacher: String,
        position: String,
        credit: Float?,
        sectionWeekRaw: String
    ): List<ImportCourseJsonModel> {
        val cleaned = sectionWeekRaw.replace("？", "").replace("?", "").trim()
        val match = Regex("""\(([^)]+)\)\s*(.*)""").find(cleaned) ?: return emptyList()
        val sectionsText = match.groupValues[1]
        val weeksText = match.groupValues[2]

        val weeks = parseWeekTokens(weeksText)
        if (weeks.isEmpty()) return emptyList()

        val sectionRanges = sectionsText.split(",", "，")
            .mapNotNull { token ->
                val nums = Regex("""(\d+)(?:\s*-\s*(\d+))?""").find(token) ?: return@mapNotNull null
                val start = nums.groupValues[1].toIntOrNull() ?: return@mapNotNull null
                val end = nums.groupValues[2].takeIf { it.isNotEmpty() }?.toIntOrNull() ?: start
                if (start <= end) start to end else end to start
            }

        return sectionRanges.map { (startSec, endSec) ->
            ImportCourseJsonModel(
                name = name,
                teacher = teacher,
                position = position,
                day = day,
                startSection = startSec,
                endSection = endSec,
                weeks = weeks,
                isCustomTime = false,
                credit = credit
            )
        }
    }

    /**
     * 解析周次文本，兼容本科及研究生多种格式：
     * - `1-16周` -> [1..16]
     * - `5-12周(单),14周` -> [5, 7, 9, 11, 14]
     * - `4-6双周` / `2-5、7-10周` / `1,3,6-8周` -> 正确展开并去重排序
     */
    fun parseWeekTokens(weeksText: String): List<Int> {
        val result = linkedSetOf<Int>()
        val tokens = weeksText.split(",", "，", "、").map { it.trim() }.filter { it.isNotEmpty() }
        for (token in tokens) {
            val isOdd = token.contains("单")
            val isEven = token.contains("双")
            val rangeMatch = Regex("""(\d+)\s*-\s*(\d+)""").find(token)
            if (rangeMatch != null) {
                val start = rangeMatch.groupValues[1].toIntOrNull() ?: continue
                val end = rangeMatch.groupValues[2].toIntOrNull() ?: continue
                val low = minOf(start, end)
                val high = maxOf(start, end)
                for (w in low..high) {
                    when {
                        isOdd && w % 2 == 1 -> result.add(w)
                        isEven && w % 2 == 0 -> result.add(w)
                        !isOdd && !isEven -> result.add(w)
                    }
                }
            } else {
                val single = Regex("""\d+""").find(token)?.value?.toIntOrNull() ?: continue
                result.add(single)
            }
        }
        return result.toList().sorted()
    }

    // ==================== 研究生课表解析 (yjsxt.cumt.edu.cn) ====================

    fun parseYjsCourseHtml(html: String): List<ImportCourseJsonModel> {
        val tableOpen = Regex("""<table\b[^>]*class\s*=\s*["'][^"']*\bGrid_Line\b[^"']*["'][^>]*>""", RegexOption.IGNORE_CASE)
            .find(html) ?: return emptyList()
        val table = extractTopLevelElements(html.substring(tableOpen.range.first), "table").firstOrNull()
            ?: return emptyList()
        val bodyHtml = extractTopLevelElements(table.innerHtml, "tbody").firstOrNull()?.innerHtml
            ?: table.innerHtml
        val rows = extractTopLevelElements(bodyHtml, "tr").drop(1)

        // 记录跨行占位：occupied[lessonNum][day] = true 表示该节次星期几已被上方 rowspan 占据
        val occupied = Array(24) { BooleanArray(8) }

        val courses = mutableListOf<ImportCourseJsonModel>()
        var lessonNum = 1
        for (row in rows) {
            val cells = extractTopLevelElements(row.innerHtml, "td")
                .filter { !Regex("""\bstyle\s*=""", RegexOption.IGNORE_CASE).containsMatchIn(it.openTagAttrs) }
            if (cells.isEmpty()) continue

            var currentDay = 1
            for (cell in cells) {
                while (currentDay <= 7 && lessonNum < occupied.size && occupied[lessonNum][currentDay]) {
                    currentDay++
                }
                if (currentDay > 7) break

                val cellText = stripHtml(cell.innerHtml)
                val rowspan = extractAttr(cell.openTagAttrs, "rowspan")?.toIntOrNull()?.coerceAtLeast(1) ?: 1
                val actualDay = currentDay
                for (r in lessonNum..(lessonNum + rowspan - 1).coerceAtMost(occupied.lastIndex)) {
                    occupied[r][actualDay] = true
                }
                if (cellText.isNotEmpty() && cellText.contains('｛')) {
                    val blocks = cellText.split("；").map { it.trim() }.filter { it.isNotEmpty() }
                    for (ce in blocks) {
                        val title = Regex("""(.*?)\s*｛""").find(ce)?.groupValues?.get(1)?.trim().orEmpty()
                        val inside = Regex("""｛(.*?)｝""").find(ce)?.groupValues?.get(1)?.trim().orEmpty()
                        if (title.isBlank() || inside.isBlank()) continue

                        for (rawSegment in inside.split("]、")) {
                            val seg = if (rawSegment.endsWith("]")) rawSegment else "$rawSegment]"
                            val weeksPart = Regex("""(.*?)\[""").find(seg)?.groupValues?.get(1)?.trim().orEmpty()
                            val weeks = parseWeekTokens(weeksPart)
                            if (weeks.isEmpty()) continue
                            val teacher = Regex("""教师:(.*?)(?=,|])""").find(seg)?.groupValues?.get(1)?.trim().orEmpty()
                            val location = Regex("""地点:(.*?)\]""").find(seg)?.groupValues?.get(1)?.trim()
                                ?: Regex("""地点:(.*?)\]""").find(ce)?.groupValues?.get(1)?.trim().orEmpty()

                            courses.add(
                                ImportCourseJsonModel(
                                    name = title,
                                    teacher = teacher,
                                    position = location,
                                    day = actualDay,
                                    startSection = lessonNum,
                                    endSection = lessonNum + rowspan - 1,
                                    weeks = weeks,
                                    isCustomTime = false
                                )
                            )
                        }
                    }
                }
                currentDay++
            }
            lessonNum++
        }
        return courses
    }

    // ==================== 考试查询解析 & 课表同步映射 ====================

    const val EXAM_COURSE_ID_PREFIX = "cumt_exam_"

    fun buildExamCourseId(tableId: String, examId: String): String {
        return "${EXAM_COURSE_ID_PREFIX}${tableId}_${examId.hashCode().toUInt().toString(16)}"
    }

    fun parseExamTimeRange(raw: String): Pair<String, String>? {
        val m = Regex("""(\d{1,2})\s*[:：]\s*(\d{2})\s*[-~—–至]\s*(\d{1,2})\s*[:：]\s*(\d{2})""")
            .find(raw) ?: return null
        val sh = m.groupValues[1].toIntOrNull() ?: return null
        val sm = m.groupValues[2].toIntOrNull() ?: return null
        val eh = m.groupValues[3].toIntOrNull() ?: return null
        val em = m.groupValues[4].toIntOrNull() ?: return null
        if (sh !in 0..23 || sm !in 0..59 || eh !in 0..23 || em !in 0..59) return null
        if (sh * 60 + sm >= eh * 60 + em) return null
        val start = "${sh.toString().padStart(2, '0')}:${sm.toString().padStart(2, '0')}"
        val end = "${eh.toString().padStart(2, '0')}:${em.toString().padStart(2, '0')}"
        return start to end
    }

    fun mapExamToScheduleSlot(
        exam: CumtExamItem,
        semesterStartDate: String?,
        firstDayOfWeek: Int,
        weekIndexCalc: (LocalDate, String?, Int) -> Int?
    ): MappedExamScheduleSlot? {
        val date = runCatching { LocalDate(exam.year, exam.month, exam.day) }.getOrNull() ?: return null
        val (start, end) = if (!exam.startTime.isNullOrBlank() && !exam.endTime.isNullOrBlank()) {
            exam.startTime to exam.endTime
        } else {
            parseExamTimeRange(exam.dateTimeRaw) ?: return null
        }
        val week = weekIndexCalc(date, semesterStartDate, firstDayOfWeek) ?: return null
        if (week !in 1..30) return null
        return MappedExamScheduleSlot(
            weekNumber = week,
            dayOfWeek = date.dayOfWeek.isoDayNumber,
            startTime = start,
            endTime = end
        )
    }

    fun parseExamHtml(html: String): List<CumtExamItem> {
        val tbody = Regex("""<tbody\b[^>]*>([\s\S]*?)</tbody>""", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.get(1) ?: html
        val rows = extractTopLevelElements(tbody, "tr")
        val results = mutableListOf<CumtExamItem>()
        val dedup = HashSet<String>()

        for (row in rows) {
            val cells = extractTopLevelElements(row.innerHtml, "td")
            if (cells.isEmpty()) continue
            val map = HashMap<String, String>()
            for (cell in cells) {
                val key = extractAttr(cell.openTagAttrs, "aria-describedby") ?: continue
                map[key] = stripHtml(cell.innerHtml)
            }
            val courseName = map["tabGrid_kcmc"].orEmpty().trim()
            val location = map["tabGrid_cdmc"].orEmpty().trim()
            val timeRaw = map["tabGrid_kssj"].orEmpty().trim()
            val seatNumber = map["tabGrid_zwh"].orEmpty().trim()
            if (courseName.isBlank() || timeRaw.isBlank()) continue

            val dateMatch = Regex("""(\d{4})-(\d{1,2})-(\d{1,2})""").find(timeRaw) ?: continue
            val year = dateMatch.groupValues[1].toIntOrNull() ?: continue
            val month = dateMatch.groupValues[2].toIntOrNull() ?: continue
            val day = dateMatch.groupValues[3].toIntOrNull() ?: continue
            val timeRange = parseExamTimeRange(timeRaw)

            val id = "$courseName|$timeRaw|$location"
            if (dedup.add(id)) {
                results.add(
                    CumtExamItem(
                        id = id,
                        courseName = courseName,
                        location = location,
                        dateTimeRaw = timeRaw,
                        year = year,
                        month = month,
                        day = day,
                        isCustom = false,
                        seatNumber = seatNumber,
                        startTime = timeRange?.first,
                        endTime = timeRange?.second
                    )
                )
            }
        }
        return results
    }

    // ==================== 成绩解析与加权计算 ====================

    fun parseScoreHtml(html: String): List<CumtScoreItem> {
        val tbody = Regex("""<tbody\b[^>]*>([\s\S]*?)</tbody>""", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.get(1) ?: html
        val rows = extractTopLevelElements(tbody, "tr")
        val results = mutableListOf<CumtScoreItem>()
        val dedup = HashSet<String>()

        for (row in rows) {
            val cells = extractTopLevelElements(row.innerHtml, "td")
            if (cells.isEmpty()) continue
            val map = HashMap<String, String>()
            for (cell in cells) {
                val key = extractAttr(cell.openTagAttrs, "aria-describedby") ?: continue
                map[key] = stripHtml(cell.innerHtml)
            }
            val courseName = map["tabGrid_kcmc"].orEmpty().trim()
            val credit = map["tabGrid_xf"]?.toDoubleOrNull() ?: continue
            val gpa = map["tabGrid_jd"]?.toDoubleOrNull()
            val scoreRaw = map["tabGrid_cj"].orEmpty().trim()
            val examType = map["tabGrid_ksxz"].orEmpty().ifBlank { "正常考试" }
            val academicYear = map["tabGrid_xnmmc"].orEmpty().trim()
            val semester = map["tabGrid_xqmmc"].orEmpty().trim()
            if (courseName.isBlank() || scoreRaw.isBlank()) continue

            val termPrefix = listOf(academicYear, semester).filter { it.isNotBlank() }.joinToString("|")
            val id = if (termPrefix.isNotEmpty()) {
                "$termPrefix|$courseName|$credit|$scoreRaw|$examType"
            } else {
                "$courseName|$credit|$scoreRaw|$examType"
            }
            if (dedup.add(id)) {
                results.add(
                    CumtScoreItem(
                        id = id,
                        courseName = courseName,
                        credit = credit,
                        gpa = gpa,
                        scoreRaw = scoreRaw,
                        examType = examType,
                        isIncluded = true,
                        academicYear = academicYear,
                        semester = semester
                    )
                )
            }
        }
        return results
    }

    fun resolveEffectiveScoreAndGpa(
        item: CumtScoreItem,
        mappings: List<CumtScoreMappingEntry>
    ): Pair<Double, Double>? {
        val numericScore = item.scoreRaw.toDoubleOrNull()
        if (numericScore != null) {
            val gpa = item.gpa ?: ((numericScore - 50.0).coerceAtLeast(0.0) / 10.0)
            return numericScore to gpa
        }
        val mapped = mappings.firstOrNull { it.rawName == item.scoreRaw } ?: return null
        return mapped.numericScore to (item.gpa ?: mapped.gpa)
    }

    fun calculateScoreSummary(
        scores: List<CumtScoreItem>,
        rate: Double,
        mappings: List<CumtScoreMappingEntry>
    ): CumtScoreSummary {
        var totalCredits = 0.0
        var weightedScoreSum = 0.0
        var weightedGpaSum = 0.0
        var includedCount = 0

        for (item in scores) {
            if (!item.isIncluded) continue
            val (scoreVal, gpaVal) = resolveEffectiveScoreAndGpa(item, mappings) ?: continue
            if (item.credit <= 0.0) continue
            totalCredits += item.credit
            weightedScoreSum += item.credit * scoreVal * rate
            weightedGpaSum += item.credit * gpaVal * rate
            includedCount++
        }

        return if (totalCredits > 0.0) {
            CumtScoreSummary(
                weightedGpa = round((weightedGpaSum / totalCredits) * 100.0) / 100.0,
                weightedScore = round((weightedScoreSum / totalCredits) * 100.0) / 100.0,
                totalCredits = round(totalCredits * 100.0) / 100.0,
                includedCount = includedCount
            )
        } else {
            CumtScoreSummary(0.0, 0.0, 0.0, 0)
        }
    }

    // ==================== 校园网与宿舍电量响应解析 ====================

    fun parseCampusNetJsonp(rawBody: String): CampusNetActionResult {
        val trimmed = rawBody.trim()
        val jsonStr = if (trimmed.startsWith("(") && (trimmed.endsWith(")") || trimmed.endsWith(");"))) {
            trimmed.substringAfter("(").substringBeforeLast(")")
        } else if (trimmed.contains("(") && trimmed.endsWith(")")) {
            trimmed.substringAfter("(").substringBeforeLast(")")
        } else {
            trimmed
        }

        return try {
            val obj = lenientJson.parseToJsonElement(jsonStr).jsonObject
            val result = obj["result"]?.jsonPrimitive?.content ?: ""
            val retCode = obj["ret_code"]?.jsonPrimitive?.content ?: ""
            val msgEncoded = obj["msg"]?.jsonPrimitive?.content ?: ""

            when {
                result == "1" -> CampusNetActionResult(success = true, message = "操作成功")
                retCode == "2" -> CampusNetActionResult(
                    success = true,
                    message = "当前设备已在线，无需重复登录",
                    alreadyLoggedIn = true
                )
                retCode == "1" -> {
                    val humanMsg = when (msgEncoded) {
                        "dXNlcmlkIGVycm9yMg==" -> "账号或密码错误"
                        "dXNlcmlkIGVycm9yMQ==" -> "账号不存在"
                        "UmFkOkxpbWl0IFVzZXJzIEVycg==" -> "在线终端数量超出限制，请前往自助服务下线多余设备"
                        "bGRhcCBhdXRoIGVycm9y" -> "密码或账号校验失败"
                        else -> runCatching {
                            CumtAesCrypto.base64Decode(msgEncoded).decodeToString().ifBlank { msgEncoded }
                        }.getOrDefault(msgEncoded.ifBlank { "认证失败 (code=1)" })
                    }
                    CampusNetActionResult(success = false, message = humanMsg)
                }
                else -> CampusNetActionResult(
                    success = false,
                    message = "校园网响应异常: result=$result, ret_code=$retCode"
                )
            }
        } catch (e: Exception) {
            CampusNetActionResult(success = false, message = "解析校园网响应失败: ${e.message ?: trimmed.take(80)}")
        }
    }

    fun extractSynjonesAuthToken(urls: List<String>): String? {
        val regex = Regex("""[?&]synjones-auth=([^&#]+)""")
        for (url in urls.asReversed()) {
            val match = regex.find(url) ?: continue
            val token = match.groupValues[1].trim()
            if (token.isNotEmpty()) return token
        }
        return null
    }

    fun resolveDormElecAid(campus: CumtDormCampus, availableAids: List<String>): String {
        val cleaned = availableAids.map { it.trim() }.filter { it.isNotEmpty() }
        if (campus.systemAid in cleaned) return campus.systemAid
        return campus.systemAid.ifBlank { cleaned.firstOrNull().orEmpty() }
    }

    fun buildDormRoomInfoJson(
        account: String,
        aid: String,
        campus: CumtDormCampus,
        building: CumtDormBuildingItem,
        roomId: String
    ): String {
        return buildJsonObject {
            put("query_elec_roominfo", buildJsonObject {
                put("account", account)
                put("aid", aid)
                put("area", buildJsonObject {
                    put("area", campus.areaId)
                    put("areaname", campus.areaName)
                })
                put("building", buildJsonObject {
                    put("building", building.name)
                    put("buildingid", building.id)
                })
                put("floor", buildJsonObject {
                    put("floor", "")
                    put("floorid", "")
                })
                put("room", buildJsonObject {
                    put("room", roomId)
                    put("roomid", roomId)
                })
            })
        }.toString()
    }

    fun parseDormPowerMessage(errmsg: String): Double? {
        return Regex("""([0-9]+(?:\.[0-9]+)?)\s*(?:度|kWh)?\s*$""", RegexOption.IGNORE_CASE)
            .find(errmsg.trim())
            ?.groupValues
            ?.get(1)
            ?.toDoubleOrNull()
    }
}
