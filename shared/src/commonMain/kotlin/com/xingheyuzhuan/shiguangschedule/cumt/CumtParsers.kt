package com.xingheyuzhuan.shiguangschedule.cumt

import com.xingheyuzhuan.shiguangschedule.data.model.CourseImportExport.ImportCourseJsonModel
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
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

private data class BkTdCell(
    val day: Int,
    val startSection: Int,
    val rowspan: Int,
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

    /**
     * 按起始标签正则与同标签嵌套深度提取元素，适用于含 class/id 过滤且可能存在包裹容器的 HTML 结构。
     */
    private fun extractMatchingElementsByOpenPattern(
        html: String,
        tagName: String,
        openPattern: Regex
    ): List<HtmlElementMatch> {
        val anyTagPattern = Regex("""<(/?)$tagName\b([^>]*)>""", RegexOption.IGNORE_CASE)
        val result = mutableListOf<HtmlElementMatch>()
        var searchFrom = 0
        while (searchFrom < html.length) {
            val openMatch = openPattern.find(html, searchFrom) ?: break
            val openAttrs = if (openMatch.groupValues.size > 1) openMatch.groupValues[1] else ""
            val openEnd = openMatch.range.last + 1
            if (openMatch.value.endsWith("/>")) {
                result.add(HtmlElementMatch(openAttrs, ""))
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
                result.add(HtmlElementMatch(openAttrs, html.substring(openEnd, closeStart)))
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

    private fun mapTermCodeToStartDate(year: Int, termCode: String): String {
        return when (termCode.trim()) {
            "1", "3" -> "$year-09-07"
            "2", "12" -> "${year + 1}-03-01"
            else -> "${year + 1}-07-06"
        }
    }

    private fun parseDayOfWeekToken(raw: String): Int? {
        val trimmed = raw.trim()
        trimmed.toIntOrNull()?.takeIf { it in 1..7 }?.let { return it }
        return when {
            trimmed.contains("一") -> 1
            trimmed.contains("二") -> 2
            trimmed.contains("三") -> 3
            trimmed.contains("四") -> 4
            trimmed.contains("五") -> 5
            trimmed.contains("六") -> 6
            trimmed.contains("日") || trimmed.contains("天") -> 7
            else -> null
        }
    }

    private fun parseSectionRanges(sectionsText: String): List<Pair<Int, Int>> {
        val normalized = sectionsText
            .replace("—", "-")
            .replace("–", "-")
            .replace("~", "-")
            .replace("至", "-")
            .replace("，", ",")
            .replace("、", ",")
        return normalized.split(",")
            .mapNotNull { token ->
                val nums = Regex("""(\d+)(?:\s*-\s*(\d+))?""").find(token.trim()) ?: return@mapNotNull null
                val start = nums.groupValues[1].toIntOrNull() ?: return@mapNotNull null
                val end = nums.groupValues[2].takeIf { it.isNotEmpty() }?.toIntOrNull() ?: start
                if (start <= 0 || end <= 0) return@mapNotNull null
                if (start <= end) start to end else end to start
            }
    }

    private fun bkCourseDedupKey(item: ImportCourseJsonModel): String =
        "${item.day}|${item.name}|${item.startSection}|${item.endSection}|${item.weeks.sorted().joinToString(",")}|${item.position}|${item.teacher}"

    fun parseSuggestedTermStartDate(html: String): String? {
        val h6Match = Regex("""<h6\b[^>]*class\s*=\s*["'][^"']*pull-left[^"']*["'][^>]*>(.*?)</h6>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(html)
        if (h6Match != null) {
            val text = stripHtml(h6Match.groupValues[1])
            val term = Regex("""学年第\s*(\d+)\s*学期""").find(text)?.groupValues?.get(1)
            val year = Regex("""(\d{4})\s*-""").find(text)?.groupValues?.get(1)?.toIntOrNull()
            if (term != null && year != null) {
                return mapTermCodeToStartDate(year, term)
            }
        }
        val xnm = Regex("""data-cumt-xnm\s*=\s*["'](\d{4})["']""", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.get(1)?.toIntOrNull() ?: return null
        val xqm = Regex("""data-cumt-xqm\s*=\s*["'](\d+)["']""", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.get(1) ?: return null
        return when (xqm) {
            "3", "12", "16" -> mapTermCodeToStartDate(xnm, xqm)
            else -> null
        }
    }

    /**
     * 第一层：直接解析正方教务 `xskbcx_cxXsgrkb.html` 返回的 `kbList` JSON，
     * 免疫不同账号个性化显示设置（齿轮隐藏字段）和表格视图切换差异。
     */
    fun parseBkCourseJson(
        rawJson: String,
        fallbackXnm: String = "",
        fallbackXqm: String = ""
    ): ParsedBkScheduleResult {
        val trimmed = rawJson.trim()
        if (trimmed.isEmpty() || !trimmed.startsWith("{")) {
            return ParsedBkScheduleResult(emptyList(), null)
        }
        val root = runCatching { lenientJson.parseToJsonElement(trimmed).jsonObject }.getOrNull()
            ?: return ParsedBkScheduleResult(emptyList(), null)

        val xsxx = runCatching { root["xsxx"]?.jsonObject }.getOrNull()
        val xnmYearFromXsxx = xsxx?.get("XNMC")?.jsonPrimitive?.contentOrNull
            ?.let { Regex("""(\d{4})""").find(it)?.groupValues?.get(1)?.toIntOrNull() }
            ?: xsxx?.get("XNM")?.jsonPrimitive?.contentOrNull?.toIntOrNull()
        val xqmFromXsxx = xsxx?.get("XQMMC")?.jsonPrimitive?.contentOrNull
            ?: xsxx?.get("XQM")?.jsonPrimitive?.contentOrNull

        val effectiveYear = xnmYearFromXsxx ?: Regex("""(\d{4})""").find(fallbackXnm)?.groupValues?.get(1)?.toIntOrNull()
        val effectiveTerm = xqmFromXsxx?.takeIf { it.isNotBlank() } ?: fallbackXqm.trim()
        val suggestedDate = if (effectiveYear != null && effectiveTerm.isNotBlank()) {
            mapTermCodeToStartDate(effectiveYear, effectiveTerm)
        } else null

        val kbList = runCatching { root["kbList"]?.jsonArray }.getOrNull()
            ?: return ParsedBkScheduleResult(emptyList(), suggestedDate)

        val courses = mutableListOf<ImportCourseJsonModel>()
        val dedup = HashSet<String>()

        for (el in kbList) {
            val obj = runCatching { el.jsonObject }.getOrNull() ?: continue
            val name = obj["kcmc"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            if (name.isBlank()) continue

            val day = obj["xqj"]?.jsonPrimitive?.contentOrNull?.let(::parseDayOfWeekToken) ?: continue
            val weeks = parseWeekTokens(obj["zcd"]?.jsonPrimitive?.contentOrNull.orEmpty())
            if (weeks.isEmpty()) continue

            val sectionText = listOf("jcs", "jcor", "jc")
                .firstNotNullOfOrNull { key ->
                    obj[key]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
                }
                .orEmpty()
            val sectionRanges = parseSectionRanges(sectionText)
            if (sectionRanges.isEmpty()) continue

            val teacher = obj["xm"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            val campus = obj["xqmc"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            val room = obj["cdmc"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            val position = if (campus.isNotBlank() && room.isNotBlank() && !room.contains(campus)) {
                "$campus $room"
            } else {
                room.ifBlank { campus }
            }
            val credit = obj["xf"]?.jsonPrimitive?.contentOrNull
                ?.replace(Regex("[^0-9.]"), "")
                ?.toFloatOrNull()

            for ((startSec, endSec) in sectionRanges) {
                val item = ImportCourseJsonModel(
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
                if (dedup.add(bkCourseDedupKey(item))) {
                    courses.add(item)
                }
            }
        }
        return ParsedBkScheduleResult(courses = courses, suggestedTermStartDate = suggestedDate)
    }

    fun parseBkCourseHtml(html: String): ParsedBkScheduleResult {
        val suggestedDate = parseSuggestedTermStartDate(html)
        val gridTableOpenRegex = Regex("""<table\b([^>]*\bid\s*=\s*["']kbgrid_table_\d+["'][^>]*)>""", RegexOption.IGNORE_CASE)
        val gridTables = extractMatchingElementsByOpenPattern(html, "table", gridTableOpenRegex)
        val searchScopes = if (gridTables.isNotEmpty()) {
            gridTables.map { it.innerHtml }
        } else {
            listOf(html)
        }

        val tdRegex = Regex("""<td\b([^>]*\bid\s*=\s*["']([1-7])-(\d+)(?:-[^"']*)?["'][^>]*)>""", RegexOption.IGNORE_CASE)
        val timetableConRegex = Regex("""<div\b([^>]*\bclass\s*=\s*["'][^"']*\btimetable_con\b[^"']*["'][^>]*)>""", RegexOption.IGNORE_CASE)

        val courses = mutableListOf<ImportCourseJsonModel>()
        val dedup = HashSet<String>()

        for (scopeHtml in searchScopes) {
            val allTds = extractAllMatchingTds(scopeHtml, tdRegex)
            for (cell in allTds) {
                val blocks = extractMatchingElementsByOpenPattern(cell.innerHtml, "div", timetableConRegex)
                    .ifEmpty { extractTopLevelElements(cell.innerHtml, "div") }
                    .ifEmpty { listOf(HtmlElementMatch("", cell.innerHtml)) }
                for (block in blocks) {
                    val parsed = parseBkSingleBlock(
                        day = cell.day,
                        blockHtml = block.innerHtml,
                        fallbackStartSection = cell.startSection,
                        fallbackRowspan = cell.rowspan
                    )
                    for (item in parsed) {
                        if (dedup.add(bkCourseDedupKey(item))) {
                            courses.add(item)
                        }
                    }
                }
            }
        }

        if (courses.isEmpty()) {
            for (item in parseBkListTableHtml(html)) {
                if (dedup.add(bkCourseDedupKey(item))) {
                    courses.add(item)
                }
            }
        }

        return ParsedBkScheduleResult(courses = courses, suggestedTermStartDate = suggestedDate)
    }

    private fun parseBkListTableHtml(html: String): List<ImportCourseJsonModel> {
        val listTableOpenRegex = Regex("""<table\b([^>]*\bid\s*=\s*["']kblist_table["'][^>]*)>""", RegexOption.IGNORE_CASE)
        val listTables = extractMatchingElementsByOpenPattern(html, "table", listTableOpenRegex)
        if (listTables.isEmpty()) return emptyList()

        val timetableConRegex = Regex("""<div\b([^>]*\bclass\s*=\s*["'][^"']*\btimetable_con\b[^"']*["'][^>]*)>""", RegexOption.IGNORE_CASE)
        val xqIdRegex = Regex("""\bid\s*=\s*["']xq_([1-7])["']""", RegexOption.IGNORE_CASE)
        val jcIdRegex = Regex("""\bid\s*=\s*["']jc_([1-7])-(\d+)-(\d+)["']""", RegexOption.IGNORE_CASE)
        val sectionTextRegex = Regex("""(\d+)\s*[-—–~至]\s*(\d+)\s*节""")

        val results = mutableListOf<ImportCourseJsonModel>()
        for (table in listTables) {
            val rows = extractTopLevelElements(table.innerHtml, "tr")
            var currentDay = 0
            for (row in rows) {
                val cells = extractTopLevelElements(row.innerHtml, "td")
                if (cells.isEmpty()) continue
                var rowStartSec = 0
                var rowEndSec = 0
                for (cell in cells) {
                    val xqMatch = xqIdRegex.find(cell.openTagAttrs)
                    if (xqMatch != null) {
                        currentDay = xqMatch.groupValues[1].toIntOrNull() ?: currentDay
                    }
                    val jcMatch = jcIdRegex.find(cell.openTagAttrs)
                    if (jcMatch != null) {
                        currentDay = jcMatch.groupValues[1].toIntOrNull() ?: currentDay
                        rowStartSec = jcMatch.groupValues[2].toIntOrNull() ?: rowStartSec
                        rowEndSec = jcMatch.groupValues[3].toIntOrNull() ?: rowEndSec
                    }
                    val cellText = stripHtml(cell.innerHtml)
                    if (cellText.startsWith("星期")) {
                        parseDayOfWeekToken(cellText)?.let { currentDay = it }
                    }
                    if (rowStartSec == 0 && !cell.innerHtml.contains("timetable_con", ignoreCase = true)) {
                        sectionTextRegex.find(cellText)?.let { m ->
                            rowStartSec = m.groupValues[1].toIntOrNull() ?: 0
                            rowEndSec = m.groupValues[2].toIntOrNull() ?: rowStartSec
                        }
                    }
                }
                if (currentDay !in 1..7) continue
                val rowspanFallback = if (rowStartSec > 0 && rowEndSec >= rowStartSec) rowEndSec - rowStartSec + 1 else 1
                val blocks = extractMatchingElementsByOpenPattern(row.innerHtml, "div", timetableConRegex)
                for (block in blocks) {
                    results.addAll(
                        parseBkSingleBlock(
                            day = currentDay,
                            blockHtml = block.innerHtml,
                            fallbackStartSection = rowStartSec,
                            fallbackRowspan = rowspanFallback
                        )
                    )
                }
            }
        }
        return results
    }

    private fun extractAllMatchingTds(
        html: String,
        tdOpenRegex: Regex
    ): List<BkTdCell> {
        val result = mutableListOf<BkTdCell>()
        val anyTd = Regex("""<(/?)td\b([^>]*)>""", RegexOption.IGNORE_CASE)
        var searchFrom = 0
        while (searchFrom < html.length) {
            val m = tdOpenRegex.find(html, searchFrom) ?: break
            val openAttrs = m.groupValues[1]
            val day = m.groupValues[2].toIntOrNull() ?: 0
            val startSection = m.groupValues[3].toIntOrNull() ?: 0
            val rowspan = extractAttr(openAttrs, "rowspan")?.toIntOrNull()?.coerceAtLeast(1) ?: 1
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
                    result.add(
                        BkTdCell(
                            day = day,
                            startSection = startSection,
                            rowspan = rowspan,
                            innerHtml = html.substring(contentStart, contentEnd)
                        )
                    )
                }
                searchFrom = closeEnd
            } else {
                break
            }
        }
        return result
    }

    private fun stripBkFieldPrefix(text: String): String {
        return text
            .replace(Regex("""^(?:上课地点|地点|场地|教室|授课教师|教师|学分|节/周|周次|周数|节次)\s*[:：]\s*"""), "")
            .trim()
    }

    private fun parseBkSingleBlock(
        day: Int,
        blockHtml: String,
        fallbackStartSection: Int = 0,
        fallbackRowspan: Int = 1
    ): List<ImportCourseJsonModel> {
        val headerSplit = Regex("""<p\b""", RegexOption.IGNORE_CASE).find(blockHtml)
        val headerSegment = if (headerSplit != null) blockHtml.substring(0, headerSplit.range.first) else blockHtml

        val title = (
            Regex(
                """<(span|u|a|font|b|strong)\b[^>]*class\s*=\s*["'][^"']*\btitle\b[^"']*["'][^>]*>(.*?)</\1>""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            ).find(blockHtml)?.groupValues?.get(2)
                ?: Regex(
                    """<(u|a|font|b|strong)\b[^>]*>(.*?)</\1>""",
                    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
                ).find(headerSegment)?.groupValues?.get(2)
                ?: headerSegment.split(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE)).firstOrNull()
            )?.let(::stripHtml).orEmpty()
        if (title.isBlank()) return emptyList()

        val pElements = extractTopLevelElements(blockHtml, "p")
        var position = ""
        var teacher = ""
        var credit: Float? = null
        var sectionWeekRaw = ""
        var weeksOnlyRaw = ""
        var sectionsOnlyRaw = ""

        val titleAttrRegex = Regex("""title\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE)
        val sectionWeekHintRegex = Regex("""[（(]\s*\d+[^）)]*节?\s*[）)].*周""")
        val weeksOnlyHintRegex = Regex("""\d+(?:\s*[-—–~,，、]\s*\d+)*\s*(?:[（(][单双][）)])?\s*周""")

        for (p in pElements) {
            val combinedAttrs = "${p.openTagAttrs} ${p.innerHtml}"
            val rawText = stripHtml(p.innerHtml)
            if (rawText.isBlank()) continue
            val cleanText = stripBkFieldPrefix(rawText)
            val attrTitle = titleAttrRegex.find(combinedAttrs)?.groupValues?.get(1)?.trim().orEmpty()

            when {
                attrTitle.contains("节/周") -> sectionWeekRaw = cleanText
                attrTitle.contains("周次") || attrTitle.contains("周数") || (attrTitle.contains("周") && !attrTitle.contains("节")) -> {
                    weeksOnlyRaw = cleanText
                }
                attrTitle.contains("节次") -> sectionsOnlyRaw = cleanText
                attrTitle.contains("地点") || attrTitle.contains("场地") || attrTitle.contains("教室") || attrTitle.contains("教学楼") -> {
                    position = cleanText
                }
                attrTitle.contains("教师") -> teacher = cleanText
                attrTitle.contains("学分") -> credit = cleanText.replace(Regex("[^0-9.]"), "").toFloatOrNull()
                combinedAttrs.contains("glyphicon-time", ignoreCase = true) ||
                    combinedAttrs.contains("glyphicon-calendar", ignoreCase = true) -> {
                    if (cleanText.contains("(") || cleanText.contains("（")) {
                        sectionWeekRaw = cleanText
                    } else if (weeksOnlyHintRegex.containsMatchIn(cleanText)) {
                        weeksOnlyRaw = cleanText
                    }
                }
                combinedAttrs.contains("glyphicon-map-marker", ignoreCase = true) -> position = cleanText
                combinedAttrs.contains("glyphicon-user", ignoreCase = true) -> teacher = cleanText
                sectionWeekHintRegex.containsMatchIn(cleanText) -> {
                    if (sectionWeekRaw.isBlank()) sectionWeekRaw = cleanText
                }
                weeksOnlyHintRegex.containsMatchIn(cleanText) -> {
                    if (sectionWeekRaw.isBlank() && weeksOnlyRaw.isBlank()) weeksOnlyRaw = cleanText
                }
            }
        }

        val effectiveSectionWeek = when {
            sectionWeekRaw.isNotBlank() -> sectionWeekRaw
            weeksOnlyRaw.isNotBlank() && sectionsOnlyRaw.isNotBlank() -> {
                val cleanSec = sectionsOnlyRaw
                    .replace("(", "").replace(")", "")
                    .replace("（", "").replace("）", "")
                    .trim()
                "($cleanSec)$weeksOnlyRaw"
            }
            weeksOnlyRaw.isNotBlank() -> weeksOnlyRaw
            else -> ""
        }
        if (effectiveSectionWeek.isBlank()) return emptyList()

        val fallbackEnd = if (fallbackStartSection > 0) fallbackStartSection + fallbackRowspan - 1 else 0
        return expandBkCourse(
            day = day,
            name = title,
            teacher = teacher,
            position = position,
            credit = credit,
            sectionWeekRaw = effectiveSectionWeek,
            fallbackStartSection = fallbackStartSection,
            fallbackEndSection = fallbackEnd
        )
    }

    fun expandBkCourse(
        day: Int,
        name: String,
        teacher: String,
        position: String,
        credit: Float?,
        sectionWeekRaw: String,
        fallbackStartSection: Int = 0,
        fallbackEndSection: Int = 0
    ): List<ImportCourseJsonModel> {
        val cleaned = sectionWeekRaw
            .replace("？", "")
            .replace("?", "")
            .replace("（", "(")
            .replace("）", ")")
            .trim()
        if (cleaned.isEmpty()) return emptyList()

        val hasDigitRegex = Regex("""\d""")
        var sectionsText = ""
        var weeksText = ""

        val standardMatch = Regex("""\(([^)]+)\)\s*(.*)""").find(cleaned)
        if (standardMatch != null &&
            hasDigitRegex.containsMatchIn(standardMatch.groupValues[1]) &&
            standardMatch.groupValues[2].isNotBlank()
        ) {
            sectionsText = standardMatch.groupValues[1]
            weeksText = standardMatch.groupValues[2]
        } else {
            val reversedMatch = Regex("""^(.*?周[^(]*(?:\([单双]\))?)\s*\(([^)]+)\)""").find(cleaned)
            if (reversedMatch != null && hasDigitRegex.containsMatchIn(reversedMatch.groupValues[2])) {
                weeksText = reversedMatch.groupValues[1]
                sectionsText = reversedMatch.groupValues[2]
            } else if (fallbackStartSection > 0) {
                weeksText = cleaned
            } else {
                return emptyList()
            }
        }

        val weeks = parseWeekTokens(weeksText)
        if (weeks.isEmpty()) return emptyList()

        val sectionRanges = if (sectionsText.isNotBlank()) {
            parseSectionRanges(sectionsText)
        } else if (fallbackStartSection > 0) {
            listOf(fallbackStartSection to maxOf(fallbackStartSection, fallbackEndSection))
        } else {
            emptyList()
        }
        if (sectionRanges.isEmpty()) return emptyList()

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

    // ==================== AI 识图课表/实验表解析 ====================

    /**
     * 解析 AI 视觉模型返回的课表/实验安排 JSON。
     * 兼容以下格式：
     * - `{"courses": [...]}` 或直接 `[...]`
     * - 被 Markdown ```json ... ``` 或说明文字包裹的响应
     * - day 支持 1..7 或中文 "星期6"/"周日"
     * - weeks 支持整数数组 `[11]`、单整数 `11`、或字符串 `"11周"` / `"11-12周"` / `"1-7单周"`
     * - 自动跳过缺少有效星期、节次或周次的未排课行
     */
    fun parseAiRecognizedCoursesJson(rawContent: String): List<CumtAiRecognizedCourse> {
        val jsonPayload = extractJsonBlock(rawContent) ?: return emptyList()
        val root = runCatching { lenientJson.parseToJsonElement(jsonPayload) }.getOrNull() ?: return emptyList()

        val itemsArray = when (root) {
            is kotlinx.serialization.json.JsonArray -> root
            is kotlinx.serialization.json.JsonObject -> {
                root["courses"] as? kotlinx.serialization.json.JsonArray
                    ?: root["items"] as? kotlinx.serialization.json.JsonArray
                    ?: root["data"] as? kotlinx.serialization.json.JsonArray
                    ?: root["schedule"] as? kotlinx.serialization.json.JsonArray
                    ?: root["experiments"] as? kotlinx.serialization.json.JsonArray
            }
            else -> null
        } ?: return emptyList()

        val results = mutableListOf<CumtAiRecognizedCourse>()
        val seenKeys = HashSet<String>()

        itemsArray.forEachIndexed { index, element ->
            val obj = element as? kotlinx.serialization.json.JsonObject ?: return@forEachIndexed
            val rawName = firstNonBlankString(obj, "name", "courseName", "title", "projectName", "项目名称", "课程名称")
                ?: return@forEachIndexed
            val name = rawName.trim()
            if (name.isBlank()) return@forEachIndexed

            val day = parseAiDayOfWeek(obj) ?: return@forEachIndexed
            val sectionPair = parseAiSectionRange(obj) ?: return@forEachIndexed
            val startSection = sectionPair.first.coerceIn(1, 20)
            val endSection = sectionPair.second.coerceIn(startSection, 20)

            val weeks = parseAiWeeksField(obj)
            if (weeks.isEmpty()) return@forEachIndexed

            val teacher = firstNonBlankString(obj, "teacher", "instructor", "教师", "老师").orEmpty().trim()
            val position = firstNonBlankString(obj, "position", "location", "room", "classroom", "lab", "地点", "教室", "实验室").orEmpty().trim()
            val remark = firstNonBlankString(obj, "remark", "note", "batch", "备注", "批次").orEmpty().trim()

            val dedupKey = "${name}|${day}|${startSection}|${endSection}|${weeks.joinToString(",")}|${position}"
            if (!seenKeys.add(dedupKey)) return@forEachIndexed

            results.add(
                CumtAiRecognizedCourse(
                    id = "ai_${index}_${dedupKey.hashCode().toUInt().toString(16)}",
                    name = name,
                    teacher = teacher,
                    position = position,
                    day = day,
                    startSection = startSection,
                    endSection = endSection,
                    weeks = weeks,
                    remark = remark,
                    selected = true
                )
            )
        }

        return results
    }

    private fun extractJsonBlock(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null

        // 优先提取 ```json ... ``` 或 ``` ... ``` 代码块
        val fenceRegex = Regex("""```(?:json)?\s*([\s\S]*?)```""", RegexOption.IGNORE_CASE)
        val fenced = fenceRegex.find(trimmed)?.groupValues?.get(1)?.trim()
        val candidate = if (!fenced.isNullOrBlank()) fenced else trimmed

        if (candidate.startsWith("{") || candidate.startsWith("[")) {
            return candidate
        }

        val firstObj = candidate.indexOf('{')
        val firstArr = candidate.indexOf('[')
        val startIdx = when {
            firstObj >= 0 && firstArr >= 0 -> minOf(firstObj, firstArr)
            firstObj >= 0 -> firstObj
            firstArr >= 0 -> firstArr
            else -> return null
        }
        val openChar = candidate[startIdx]
        val closeChar = if (openChar == '{') '}' else ']'
        val endIdx = candidate.lastIndexOf(closeChar)
        if (endIdx <= startIdx) return null
        return candidate.substring(startIdx, endIdx + 1)
    }

    private fun firstNonBlankString(obj: kotlinx.serialization.json.JsonObject, vararg keys: String): String? {
        for (key in keys) {
            val elem = obj[key] ?: continue
            val text = (elem as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull?.trim()
            if (!text.isNullOrBlank() && !text.equals("null", ignoreCase = true)) {
                return text
            }
        }
        return null
    }

    private fun parseAiDayOfWeek(obj: kotlinx.serialization.json.JsonObject): Int? {
        val raw = firstNonBlankString(obj, "day", "dayOfWeek", "weekday", "weekDay", "星期", "周几") ?: return null
        raw.toIntOrNull()?.let { n ->
            return if (n in 1..7) n else null
        }
        return when {
            raw.contains("一") || raw.contains("1") -> 1
            raw.contains("二") || raw.contains("2") -> 2
            raw.contains("三") || raw.contains("3") -> 3
            raw.contains("四") || raw.contains("4") -> 4
            raw.contains("五") || raw.contains("5") -> 5
            raw.contains("六") || raw.contains("6") -> 6
            raw.contains("日") || raw.contains("天") || raw.contains("7") -> 7
            else -> null
        }
    }

    private fun parseAiSectionRange(obj: kotlinx.serialization.json.JsonObject): Pair<Int, Int>? {
        val startDirect = firstNonBlankString(obj, "startSection", "start_section", "start", "起始节")
            ?.let { Regex("""\d+""").find(it)?.value?.toIntOrNull() }
        val endDirect = firstNonBlankString(obj, "endSection", "end_section", "end", "结束节")
            ?.let { Regex("""\d+""").find(it)?.value?.toIntOrNull() }
        if (startDirect != null && startDirect in 1..20) {
            val resolvedEnd = (endDirect ?: startDirect).coerceAtLeast(startDirect)
            return startDirect to resolvedEnd
        }

        val combined = firstNonBlankString(obj, "sections", "section", "period", "节次", "节") ?: return null
        val nums = Regex("""\d+""").findAll(combined).mapNotNull { it.value.toIntOrNull() }.toList()
        if (nums.isEmpty()) return null
        val s = nums.first()
        val e = nums.last().coerceAtLeast(s)
        return if (s in 1..20) s to e else null
    }

    private fun parseAiWeeksField(obj: kotlinx.serialization.json.JsonObject): List<Int> {
        val keys = listOf("weeks", "week", "weekList", "周次", "周")
        for (key in keys) {
            val elem = obj[key] ?: continue
            when (elem) {
                is kotlinx.serialization.json.JsonArray -> {
                    val ints = elem.mapNotNull { item ->
                        val prim = item as? kotlinx.serialization.json.JsonPrimitive ?: return@mapNotNull null
                        prim.contentOrNull?.trim()?.let { text ->
                            text.toIntOrNull() ?: Regex("""\d+""").find(text)?.value?.toIntOrNull()
                        }
                    }.filter { it in 1..30 }.distinct().sorted()
                    if (ints.isNotEmpty()) return ints
                }
                is kotlinx.serialization.json.JsonPrimitive -> {
                    val text = elem.contentOrNull?.trim().orEmpty()
                    if (text.isNotEmpty() && !text.equals("null", ignoreCase = true)) {
                        val parsed = parseManualWeeksInput(text)
                        if (parsed.isNotEmpty()) return parsed
                    }
                }
                else -> Unit
            }
        }
        return emptyList()
    }

    fun parseManualWeeksInput(input: String): List<Int> {
        val cleaned = input.trim()
        if (cleaned.isEmpty()) return emptyList()
        // 复用现有的矿大周次解析器（支持 "1-4周", "1-7单周", "11,13周" 等）
        val byExisting = parseWeekTokens(cleaned).filter { it in 1..30 }
        if (byExisting.isNotEmpty()) return byExisting
        return Regex("""\d+""").findAll(cleaned)
            .mapNotNull { it.value.toIntOrNull() }
            .filter { it in 1..30 }
            .distinct()
            .sorted()
            .toList()
    }

    fun formatWeeksCompact(weeks: List<Int>): String {
        val sorted = weeks.filter { it > 0 }.distinct().sorted()
        if (sorted.isEmpty()) return ""
        val parts = mutableListOf<String>()
        var rangeStart = sorted.first()
        var prev = sorted.first()
        for (i in 1 until sorted.size) {
            val cur = sorted[i]
            if (cur == prev + 1) {
                prev = cur
            } else {
                parts.add(if (rangeStart == prev) "$rangeStart" else "$rangeStart-$prev")
                rangeStart = cur
                prev = cur
            }
        }
        parts.add(if (rangeStart == prev) "$rangeStart" else "$rangeStart-$prev")
        return parts.joinToString(",")
    }

    fun formatDayOfWeekLabel(day: Int): String = when (day) {
        1 -> "周一"
        2 -> "周二"
        3 -> "周三"
        4 -> "周四"
        5 -> "周五"
        6 -> "周六"
        7 -> "周日"
        else -> "周$day"
    }
}
