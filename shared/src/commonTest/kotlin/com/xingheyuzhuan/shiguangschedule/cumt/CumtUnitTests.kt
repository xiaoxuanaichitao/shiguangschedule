package com.xingheyuzhuan.shiguangschedule.cumt

import com.xingheyuzhuan.shiguangschedule.data.model.ScheduleGridStyle
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CumtUnitTests {

    @Test
    fun aesBase64RoundTripAndPadding() {
        val raw = "cumt-2026-test-payload!"
        val encoded = CumtAesCrypto.base64Encode(raw.encodeToByteArray())
        val decoded = CumtAesCrypto.base64Decode(encoded).decodeToString()
        assertEquals(raw, decoded)

        // 与 FlyingKxz DR.COM 错误消息一致的 Base64 向量
        assertEquals("userid error2", CumtAesCrypto.base64Decode("dXNlcmlkIGVycm9yMg==").decodeToString())
        assertEquals(
            "Rad:Limit Users Err",
            CumtAesCrypto.base64Decode("UmFkOkxpbWl0IFVzZXJzIEVycg==").decodeToString()
        )
    }

    @Test
    fun parseBkCourseHtmlHandlesMultiSectionOddEvenAndTermStartDate() {
        val sampleHtml = """
            <div>
              <h6 class="pull-left">2025-2026学年第1学期张三的课表</h6>
              <table id="kbgrid_table_0">
                <tr>
                  <td id="1-1">
                    <div class="timetable_con">
                      <span class="title">高等数学A(1)</span>
                      <p><span title="节/周"></span>(1-3节,5-6节)1-5周,7-9周？</p>
                      <p><span title="上课地点"></span>博学楼1号楼A101</p>
                      <p><span title="教师 "></span>李老师</p>
                      <p><span title="学分"></span>5.5</p>
                    </div>
                  </td>
                  <td id="3-3">
                    <div class="timetable_con">
                      <u class="title showJxbtkjl">大学物理实验</u>
                      <p><span title="节/周"></span>(3-4节)5-12周(单),14周</p>
                      <p><span title="上课地点"></span>物理实验中心</p>
                      <p><span title="教师"></span>王老师</p>
                      <p><span title="学分"></span>1.5</p>
                    </div>
                  </td>
                </tr>
                <tr>
                  <td id="1-5">
                    <div class="timetable_con">
                      <span class="title">高等数学A(1)</span>
                      <p><span title="节/周"></span>(1-3节,5-6节)1-5周,7-9周</p>
                      <p><span title="上课地点"></span>博学楼1号楼A101</p>
                      <p><span title="教师 "></span>李老师</p>
                      <p><span title="学分"></span>5.5</p>
                    </div>
                  </td>
                </tr>
              </table>
            </div>
        """.trimIndent()

        val result = CumtParsers.parseBkCourseHtml(sampleHtml)
        assertEquals("2025-09-07", result.suggestedTermStartDate)
        assertEquals(3, result.courses.size)

        val math1 = result.courses.first { it.name == "高等数学A(1)" && it.startSection == 1 }
        assertEquals(1, math1.day)
        assertEquals(3, math1.endSection)
        assertEquals(listOf(1, 2, 3, 4, 5, 7, 8, 9), math1.weeks)
        assertEquals("博学楼1号楼A101", math1.position)
        assertEquals("李老师", math1.teacher)
        assertEquals(5.5f, math1.credit)

        val math2 = result.courses.first { it.name == "高等数学A(1)" && it.startSection == 5 }
        assertEquals(6, math2.endSection)

        val physics = result.courses.first { it.name == "大学物理实验" }
        assertEquals(3, physics.day)
        assertEquals(listOf(5, 7, 9, 11, 14), physics.weeks)
        assertEquals(1.5f, physics.credit)
    }

    @Test
    fun parseYjsCourseHtmlHandlesRowspanShiftAndMultipleSegments() {
        val sampleHtml = """
            <table class="Grid_Line">
              <tbody>
                <tr><th>节次</th><th>一</th><th>二</th><th>三</th><th>四</th><th>五</th><th>六</th><th>日</th></tr>
                <tr>
                  <td style="width:60px;">第1节</td>
                  <td rowspan="2">高等数值分析 ｛2-5、7-10周[教师:张教授,地点:松苑201]｝</td>
                  <td></td>
                  <td rowspan="2">机器学习 ｛4-6双周[教师:刘老师,地点:计算机楼B202]｝；工程伦理 ｛10-13周[教师:陈老师,地点:教三201]｝</td>
                  <td></td><td></td><td></td><td></td>
                </tr>
                <tr>
                  <td style="width:60px;">第2节</td>
                  <td></td>
                  <td rowspan="1">文献检索 ｛1-4周[教师:赵老师,地点:图书馆]｝</td>
                  <td></td><td></td><td></td>
                </tr>
              </tbody>
            </table>
        """.trimIndent()

        val courses = CumtParsers.parseYjsCourseHtml(sampleHtml)
        assertEquals(4, courses.size)

        val numerical = courses.first { it.name == "高等数值分析" }
        assertEquals(1, numerical.day)
        assertEquals(1, numerical.startSection)
        assertEquals(2, numerical.endSection)
        assertEquals(listOf(2, 3, 4, 5, 7, 8, 9, 10), numerical.weeks)
        assertEquals("张教授", numerical.teacher)
        assertEquals("松苑201", numerical.position)

        val ml = courses.first { it.name == "机器学习" }
        assertEquals(3, ml.day)
        assertEquals(listOf(4, 6), ml.weeks)

        // 第2行第2个非空白单元格对应星期四（因第1列与第3列已在第1行被rowspan=2占据）
        val retrieval = courses.first { it.name == "文献检索" }
        assertEquals(4, retrieval.day)
        assertEquals(2, retrieval.startSection)
        assertEquals(2, retrieval.endSection)
    }

    @Test
    fun parseExamAndScoreHtmlAndComputeGpaAccurately() {
        val examHtml = """
            <table id="tabGrid">
              <tbody>
                <tr class="jqgfirstrow"><td></td></tr>
                <tr>
                  <td aria-describedby="tabGrid_kcmc">数据结构</td>
                  <td aria-describedby="tabGrid_cdmc">博五B102</td>
                  <td aria-describedby="tabGrid_kssj">2026-06-20(09:00-11:00)</td>
                  <td aria-describedby="tabGrid_zwh">24</td>
                </tr>
              </tbody>
            </table>
        """.trimIndent()
        val exams = CumtParsers.parseExamHtml(examHtml)
        assertEquals(1, exams.size)
        assertEquals("数据结构", exams[0].courseName)
        assertEquals("博五B102", exams[0].location)
        assertEquals("24", exams[0].seatNumber)
        assertEquals("09:00", exams[0].startTime)
        assertEquals("11:00", exams[0].endTime)
        assertEquals(2026, exams[0].year)
        assertEquals(6, exams[0].month)
        assertEquals(20, exams[0].day)

        val scoreHtml = """
            <table id="tabGrid">
              <tbody>
                <tr>
                  <td aria-describedby="tabGrid_kcmc">线性代数</td>
                  <td aria-describedby="tabGrid_xf">3.0</td>
                  <td aria-describedby="tabGrid_jd">4.0</td>
                  <td aria-describedby="tabGrid_cj">90</td>
                  <td aria-describedby="tabGrid_ksxz">正常考试</td>
                </tr>
                <tr>
                  <td aria-describedby="tabGrid_kcmc">体育</td>
                  <td aria-describedby="tabGrid_xf">1.0</td>
                  <td aria-describedby="tabGrid_jd"></td>
                  <td aria-describedby="tabGrid_cj">优秀</td>
                  <td aria-describedby="tabGrid_ksxz">正常考试</td>
                </tr>
              </tbody>
            </table>
        """.trimIndent()
        val scores = CumtParsers.parseScoreHtml(scoreHtml)
        assertEquals(2, scores.size)

        val summary = CumtParsers.calculateScoreSummary(
            scores = scores,
            rate = 1.0,
            mappings = CumtDefaultScoreMappings.entries
        )
        // GPA = (3.0 * 4.0 + 1.0 * 4.5) / 4.0 = 4.125 -> 4.13 (or 4.12 round)
        // Score = (3.0 * 90 + 1.0 * 90) / 4.0 = 90.0
        assertEquals(4.0, summary.totalCredits)
        assertEquals(90.0, summary.weightedScore)
        assertTrue(summary.weightedGpa in 4.12..4.13)

        val excluded = listOf(scores[0], scores[1].copy(isIncluded = false))
        val subSummary = CumtParsers.calculateScoreSummary(excluded, 1.2, CumtDefaultScoreMappings.entries)
        assertEquals(3.0, subSummary.totalCredits)
        assertEquals(4.8, subSummary.weightedGpa)
        assertEquals(108.0, subSummary.weightedScore)
    }

    @Test
    fun parseCampusNetJsonpAndDormPowerAndBusSoonWindow() {
        val okRes = CumtParsers.parseCampusNetJsonp("""({"result":"1","msg":"login ok"})""")
        assertTrue(okRes.success)

        val onlineRes = CumtParsers.parseCampusNetJsonp("""({"result":"0","ret_code":"2"})""")
        assertTrue(onlineRes.success && onlineRes.alreadyLoggedIn)

        val badPwd = CumtParsers.parseCampusNetJsonp("""({"result":"0","ret_code":"1","msg":"dXNlcmlkIGVycm9yMg=="})""")
        assertTrue(!badPwd.success && badPwd.message.contains("账号或密码错误"))

        val power = CumtParsers.parseDormPowerMessage("剩余电量：78.45")
        assertNotNull(power)
        assertEquals(78.45, power)

        assertTrue(CumtBusData.nNorthWork.isNotEmpty())
        assertEquals(15, CumtBusData.getSoonMinutes("14:30", 14, 15))
        assertNull(CumtBusData.getSoonMinutes("16:00", 14, 15))
    }

    private fun calcWeekIndexForTest(
        targetDate: LocalDate,
        startDateStr: String?,
        firstDayOfWeekInt: Int
    ): Int? {
        if (startDateStr.isNullOrEmpty()) return null
        val parsedStart = LocalDate.parse(startDateStr)
        val startOffset = (parsedStart.dayOfWeek.isoDayNumber - firstDayOfWeekInt + 7) % 7
        val targetOffset = (targetDate.dayOfWeek.isoDayNumber - firstDayOfWeekInt + 7) % 7
        val alignedStart = parsedStart.minus(startOffset, DateTimeUnit.DAY)
        val alignedTarget = targetDate.minus(targetOffset, DateTimeUnit.DAY)
        val diffDays = alignedTarget.toEpochDays() - alignedStart.toEpochDays()
        return (diffDays / 7).toInt() + 1
    }

    @Test
    fun parseExamTimeRangeAndBuildExamCourseId() {
        assertEquals("09:00" to "11:00", CumtParsers.parseExamTimeRange("2026-06-20(09:00-11:00)"))
        assertEquals("08:30" to "10:30", CumtParsers.parseExamTimeRange("2026-06-20 8：30至10：30"))
        assertNull(CumtParsers.parseExamTimeRange("11:00-09:00"))
        assertNull(CumtParsers.parseExamTimeRange("2026-06-20"))

        val id1 = CumtParsers.buildExamCourseId("tableA", "exam1")
        val id1Again = CumtParsers.buildExamCourseId("tableA", "exam1")
        val idOtherTable = CumtParsers.buildExamCourseId("tableB", "exam1")
        val idOtherExam = CumtParsers.buildExamCourseId("tableA", "exam2")

        assertTrue(id1.startsWith(CumtParsers.EXAM_COURSE_ID_PREFIX))
        assertEquals(id1, id1Again)
        assertNotEquals(id1, idOtherTable)
        assertNotEquals(id1, idOtherExam)
    }

    @Test
    fun mapExamToScheduleSlotHandlesWeekdayWeekendExtendedWeeksAndInvalidCases() {
        val semesterStart = "2026-03-02" // 星期一
        val mondayFirstDay = DayOfWeek.MONDAY.isoDayNumber

        val weekdayExam = CumtExamItem(
            id = "e1",
            courseName = "数据结构",
            location = "博五B102",
            dateTimeRaw = "2026-03-04(09:00-11:00)",
            year = 2026,
            month = 3,
            day = 4,
            startTime = "09:00",
            endTime = "11:00"
        )
        val weekdaySlot = CumtParsers.mapExamToScheduleSlot(
            exam = weekdayExam,
            semesterStartDate = semesterStart,
            firstDayOfWeek = mondayFirstDay,
            weekIndexCalc = ::calcWeekIndexForTest
        )
        assertNotNull(weekdaySlot)
        assertEquals(1, weekdaySlot.weekNumber)
        assertEquals(3, weekdaySlot.dayOfWeek)
        assertEquals("09:00", weekdaySlot.startTime)
        assertEquals("11:00", weekdaySlot.endTime)

        val weekendExam = weekdayExam.copy(
            id = "e2",
            dateTimeRaw = "2026-03-07 14:30-16:30",
            day = 7,
            startTime = null,
            endTime = null
        )
        val weekendSlot = CumtParsers.mapExamToScheduleSlot(
            exam = weekendExam,
            semesterStartDate = semesterStart,
            firstDayOfWeek = mondayFirstDay,
            weekIndexCalc = ::calcWeekIndexForTest
        )
        assertNotNull(weekendSlot)
        assertEquals(1, weekendSlot.weekNumber)
        assertEquals(6, weekendSlot.dayOfWeek)
        assertEquals("14:30", weekendSlot.startTime)
        assertEquals("16:30", weekendSlot.endTime)

        // 第 21 周 (> 20) 同样应被保留映射
        val week21Exam = weekdayExam.copy(id = "e3", month = 7, day = 20, dateTimeRaw = "2026-07-20(09:00-11:00)")
        val week21Slot = CumtParsers.mapExamToScheduleSlot(
            exam = week21Exam,
            semesterStartDate = semesterStart,
            firstDayOfWeek = mondayFirstDay,
            weekIndexCalc = ::calcWeekIndexForTest
        )
        assertNotNull(week21Slot)
        assertEquals(21, week21Slot.weekNumber)
        assertEquals(1, week21Slot.dayOfWeek)

        // 开学前日期 (week <= 0) 应返回 null
        val preSemesterExam = weekdayExam.copy(id = "e4", month = 2, day = 25, dateTimeRaw = "2026-02-25(09:00-11:00)")
        assertNull(
            CumtParsers.mapExamToScheduleSlot(
                exam = preSemesterExam,
                semesterStartDate = semesterStart,
                firstDayOfWeek = mondayFirstDay,
                weekIndexCalc = ::calcWeekIndexForTest
            )
        )

        // 缺少起止时间应返回 null
        val noTimeExam = weekdayExam.copy(id = "e5", dateTimeRaw = "2026-03-04", startTime = null, endTime = null)
        assertNull(
            CumtParsers.mapExamToScheduleSlot(
                exam = noTimeExam,
                semesterStartDate = semesterStart,
                firstDayOfWeek = mondayFirstDay,
                weekIndexCalc = ::calcWeekIndexForTest
            )
        )
    }

    @Test
    fun mobileAdaptationJsForCumtAndNonCumt() {
        val jwxtJs = CumtWebScripts.mobileAdaptationJs("http://jwxt.cumt.edu.cn/jwglxt/xtgl/login_slogin.html")
        assertNotNull(jwxtJs)
        assertTrue(jwxtJs.contains("viewport"))
        assertTrue(jwxtJs.contains("cumt-mobile-adapt"))

        val jwxtScoreCss = CumtWebScripts.mobileAdaptationJs("http://jwxt.cumt.edu.cn/jwglxt/cjcx/cjcx_cxDgXscj.html?gnmkdm=N305005")
        assertNotNull(jwxtScoreCss)
        assertTrue(jwxtScoreCss.contains(".chosen-container"))
        assertTrue(jwxtScoreCss.contains("select, select.form-control"))

        assertNull(CumtWebScripts.mobileAdaptationJs("https://example.com/"))
    }

    @Test
    fun dormCatalogCoversDualCampusesAndLegacyAliases() {
        assertEquals(6, CumtDormCatalog.nanhuBuildings.size)
        assertEquals(19, CumtDormCatalog.wenchangBuildings.size)
        assertEquals("1", CumtDormCampus.NANHU.areaId)
        assertEquals("中国矿业大学", CumtDormCampus.NANHU.areaName)
        assertEquals("0030000000002501", CumtDormCampus.NANHU.systemAid)
        assertEquals("22", CumtDormCampus.WENCHANG.areaId)
        assertEquals("中国矿业大学文昌校区", CumtDormCampus.WENCHANG.areaName)
        assertEquals("0030000000002502", CumtDormCampus.WENCHANG.systemAid)

        val resolvedLegacy = CumtDormCatalog.resolveBuilding(
            campusId = "nanhu",
            buildingId = null,
            buildingName = "研梅"
        )
        assertEquals("14", resolvedLegacy.id)
        assertEquals("兰梅", resolvedLegacy.name)

        val resolvedWenchang = CumtDormCatalog.resolveBuilding(
            campusId = "wenchang",
            buildingId = "311"
        )
        assertEquals("行健楼", resolvedWenchang.name)
    }

    @Test
    fun dormPowerHelpersExtractTokenResolveAidAndBuildPayload() {
        val token = CumtParsers.extractSynjonesAuthToken(
            listOf(
                "https://authserver.cumt.edu.cn/authserver/login",
                "https://yktm.cumt.edu.cn/plat-pc/?name=loginTransit&synjones-auth=abc.jwt.token_123"
            )
        )
        assertEquals("abc.jwt.token_123", token)
        assertNull(CumtParsers.extractSynjonesAuthToken(listOf("https://yktm.cumt.edu.cn/plat/shouyeUser")))

        assertEquals(
            "0030000000002502",
            CumtParsers.resolveDormElecAid(CumtDormCampus.WENCHANG, listOf("0030000000002501", "0030000000002502"))
        )
        assertEquals(
            "0030000000002501",
            CumtParsers.resolveDormElecAid(CumtDormCampus.NANHU, emptyList())
        )

        val building = CumtDormCatalog.resolveBuilding("nanhu", "14")
        val payload = CumtParsers.buildDormRoomInfoJson(
            account = "123456",
            aid = CumtDormCampus.NANHU.systemAid,
            campus = CumtDormCampus.NANHU,
            building = building,
            roomId = "S3B405"
        )
        assertTrue(payload.contains("\"account\":\"123456\""))
        assertTrue(payload.contains("\"aid\":\"0030000000002501\""))
        assertTrue(payload.contains("\"areaname\":\"中国矿业大学\""))
        assertTrue(payload.contains("\"building\":\"兰梅\""))
        assertTrue(payload.contains("\"buildingid\":\"14\""))
        assertTrue(payload.contains("\"roomid\":\"S3B405\""))

        assertEquals(42.5, CumtParsers.parseDormPowerMessage("当前剩余电量42.5度"))
        assertEquals(18.0, CumtParsers.parseDormPowerMessage("Remaining 18 kWh"))
        assertNull(CumtParsers.parseDormPowerMessage("未查询到该房间信息"))
    }

    @Test
    fun parseScoreWithTermFieldsAndSuggestedStartDateFallback() {
        val scoreHtml = """
            <table id="tabGrid">
              <tbody>
                <tr>
                  <td aria-describedby="tabGrid_xnmmc">2025-2026</td>
                  <td aria-describedby="tabGrid_xqmmc">1</td>
                  <td aria-describedby="tabGrid_kcmc">大学体育</td>
                  <td aria-describedby="tabGrid_xf">1.0</td>
                  <td aria-describedby="tabGrid_jd">4.0</td>
                  <td aria-describedby="tabGrid_cj">92</td>
                  <td aria-describedby="tabGrid_ksxz">正常考试</td>
                </tr>
                <tr>
                  <td aria-describedby="tabGrid_xnmmc">2025-2026</td>
                  <td aria-describedby="tabGrid_xqmmc">2</td>
                  <td aria-describedby="tabGrid_kcmc">大学体育</td>
                  <td aria-describedby="tabGrid_xf">1.0</td>
                  <td aria-describedby="tabGrid_jd">4.0</td>
                  <td aria-describedby="tabGrid_cj">92</td>
                  <td aria-describedby="tabGrid_ksxz">正常考试</td>
                </tr>
              </tbody>
            </table>
        """.trimIndent()

        val scores = CumtParsers.parseScoreHtml(scoreHtml)
        assertEquals(2, scores.size)
        assertEquals("2025-2026", scores[0].academicYear)
        assertEquals("1", scores[0].semester)
        assertEquals("2", scores[1].semester)
        assertNotEquals(scores[0].id, scores[1].id)

        assertEquals(
            "2026-03-01",
            CumtParsers.parseSuggestedTermStartDate("""<div id="cumt-meta" data-cumt-xnm="2025" data-cumt-xqm="12"></div>""")
        )
        assertEquals(
            "2025-09-07",
            CumtParsers.parseSuggestedTermStartDate("""<div id="cumt-meta" data-cumt-xnm="2025" data-cumt-xqm="3"></div>""")
        )
    }

    @Test
    fun jwxtScriptsAndConstantsMatchUpdatedRules() {
        assertTrue(CumtConstants.JWXT_SCHEDULE_URL.contains("gnmkdm=N2151"))
        assertTrue(CumtConstants.YKT_RECHARGE_URL.endsWith("/plat/shouyeUser"))

        val autoNav = CumtWebScripts.jwxtAutoNavigateJs(
            CumtConstants.JWXT_MENU_SCORE,
            "/jwglxt/cjcx/cjcx_cxDgXscj.html?gnmkdm=N305005&layout=default"
        )
        assertTrue(autoNav.contains("N305005"))
        assertTrue(autoNav.contains("cjcx_cxDgXscj.html"))

        val stateJson = """
            "{\"hasSelectors\":true,\"years\":[{\"value\":\"\",\"label\":\"全部\",\"selected\":true},{\"value\":\"2025\",\"label\":\"2025-2026\",\"selected\":false}],\"terms\":[{\"value\":\"3\",\"label\":\"1\",\"selected\":false}],\"selectedYear\":\"\",\"selectedTerm\":\"\",\"rowCount\":12,\"currentPage\":1,\"totalPages\":3,\"isLoading\":false,\"hasNextPage\":true}"
        """.trim()
        val parsed = CumtWebScripts.parseJwxtTermOptionsState(stateJson)
        assertTrue(parsed.hasSelectors)
        assertEquals(2, parsed.years.size)
        assertEquals("全部", parsed.years[0].label)
        assertEquals(12, parsed.rowCount)
        assertEquals(3, parsed.totalPages)
        assertTrue(parsed.hasNextPage)

        assertEquals(false, CumtWebScripts.parseJwxtTermOptionsState(null).hasSelectors)
    }

    @Test
    fun scheduleGridStyleDefaultMatchesRedesignedDefaults() {
        val style = ScheduleGridStyle.DEFAULT
        assertEquals(12, style.courseColorMaps.size)
        assertTrue(style.hideGridLines)
        assertTrue(style.hideTeacher)
        assertTrue(style.removeLocationAt)
        assertTrue(style.textAlignCenterHorizontal)
        assertTrue(style.textAlignCenterVertical)
        assertEquals(11f, style.courseBlockCornerRadiusDp)
        assertEquals(2f, style.courseBlockOuterPaddingDp)
    }
}
