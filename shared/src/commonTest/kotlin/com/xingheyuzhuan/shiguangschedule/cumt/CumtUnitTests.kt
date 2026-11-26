package com.xingheyuzhuan.shiguangschedule.cumt

import androidx.compose.ui.unit.Density
import com.xingheyuzhuan.shiguangschedule.data.model.ScheduleGridStyle
import com.xingheyuzhuan.shiguangschedule.ui.theme.withAppFontScale
import com.xingheyuzhuan.shiguangschedule.ui.theme.withDefaultFontScale
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
        assertTrue(jwxtJs.contains("flex-wrap: wrap"))
        assertTrue(jwxtJs.contains("min-width: 120px"))
        assertTrue(jwxtJs.contains("max-width: 140px"))
        assertTrue(jwxtJs.contains("fixLoginCaptchaRow"))

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
    fun parseBkCourseJsonHandlesFullPayloadAndFallback() {
        val sampleJson = """
            {
              "xsxx": {
                "XNMC": "2026-2027",
                "XQMMC": "1"
              },
              "kbList": [
                {
                  "kcmc": "数据结构与算法",
                  "xm": "李老师",
                  "xqmc": "南湖校区",
                  "cdmc": "博4-A201",
                  "xqj": "1",
                  "jcs": "1-3,5-6",
                  "zcd": "1-12周(单),14周",
                  "xf": "4.0"
                },
                {
                  "kcmc": "自动控制原理",
                  "xm": "王老师",
                  "xqmc": "南湖校区",
                  "cdmc": "南湖校区 博5-B101",
                  "xqj": "二",
                  "jcor": "3-4节",
                  "zcd": "2-16周(双)",
                  "xf": "3.5"
                }
              ]
            }
        """.trimIndent()

        val result = CumtParsers.parseBkCourseJson(sampleJson)
        assertEquals("2026-09-07", result.suggestedTermStartDate)
        assertEquals(3, result.courses.size)

        val ds1 = result.courses.first { it.name == "数据结构与算法" && it.startSection == 1 }
        assertEquals(1, ds1.day)
        assertEquals(3, ds1.endSection)
        assertEquals("南湖校区 博4-A201", ds1.position)
        assertEquals("李老师", ds1.teacher)
        assertEquals(4.0f, ds1.credit)
        assertEquals(listOf(1, 3, 5, 7, 9, 11, 14), ds1.weeks)

        val autoCtrl = result.courses.first { it.name == "自动控制原理" }
        assertEquals(2, autoCtrl.day)
        assertEquals(3, autoCtrl.startSection)
        assertEquals(4, autoCtrl.endSection)
        // cdmc 已包含 xqmc 时不重复拼接
        assertEquals("南湖校区 博5-B101", autoCtrl.position)
        assertEquals(listOf(2, 4, 6, 8, 10, 12, 14, 16), autoCtrl.weeks)

        val fallbackDateRes = CumtParsers.parseBkCourseJson("""{"kbList":[]}""", fallbackXnm = "2026", fallbackXqm = "12")
        assertTrue(fallbackDateRes.courses.isEmpty())
        assertEquals("2027-03-01", fallbackDateRes.suggestedTermStartDate)

        val malformed = CumtParsers.parseBkCourseJson("not-json")
        assertTrue(malformed.courses.isEmpty())
        assertNull(malformed.suggestedTermStartDate)
    }

    @Test
    fun parseBkCourseHtmlHandlesEdgeCasesAndListFallback() {
        // 1. 蓝色 <font>/<a> 无 class="title"，全角括号，周次前置，缺失“节/周”按周次+rowspan 回退，以及 kbgrid_table_1
        val edgeGridHtml = """
            <div id="cumt-meta" data-cumt-xnm="2026" data-cumt-xqm="3"></div>
            <table id="kbgrid_table_0">
              <tr>
                <td id="1-1" rowspan="2">
                  <div class="wrapper">
                    <div class="timetable_con">
                      <font color="blue">数据结构A</font>
                      <p><span title="节/周"></span>（1-2节）1-16周</p>
                      <p><span title="地点"></span>地点：博学楼B101</p>
                      <p><span title="授课教师"></span>教师：张老师</p>
                    </div>
                    <div class="timetable_con">
                      <a href="javascript:void(0)">离散数学</a>
                      <p><span title="周数"></span>5-12周(单)</p>
                      <p><span class="glyphicon glyphicon-user"></span>陈老师</p>
                    </div>
                  </div>
                </td>
                <td id="2-3" rowspan="2">
                  <div class="timetable_con">
                    <span class="title">大学英语</span>
                    <p><span title="节/周"></span>1-8周(3-4节)</p>
                    <p><span class="glyphicon glyphicon-map-marker"></span>教一102</p>
                  </div>
                </td>
              </tr>
            </table>
            <table id="kbgrid_table_1">
              <tr>
                <td id="4-9" rowspan="2">
                  <div class="timetable_con">
                    <span class="title">科研素养</span>
                    <p>1-4周</p>
                  </div>
                </td>
              </tr>
            </table>
        """.trimIndent()

        val gridRes = CumtParsers.parseBkCourseHtml(edgeGridHtml)
        assertEquals("2026-09-07", gridRes.suggestedTermStartDate)
        assertEquals(4, gridRes.courses.size)

        val ds = gridRes.courses.first { it.name == "数据结构A" }
        assertEquals("博学楼B101", ds.position)
        assertEquals("张老师", ds.teacher)
        assertEquals(1, ds.startSection)
        assertEquals(2, ds.endSection)

        val discrete = gridRes.courses.first { it.name == "离散数学" }
        assertEquals(1, discrete.day)
        assertEquals(1, discrete.startSection)
        assertEquals(2, discrete.endSection)
        assertEquals("陈老师", discrete.teacher)
        assertEquals(listOf(5, 7, 9, 11), discrete.weeks)

        val eng = gridRes.courses.first { it.name == "大学英语" }
        assertEquals(2, eng.day)
        assertEquals(3, eng.startSection)
        assertEquals(4, eng.endSection)
        assertEquals("教一102", eng.position)
        assertEquals((1..8).toList(), eng.weeks)

        val extraTable = gridRes.courses.first { it.name == "科研素养" }
        assertEquals(4, extraTable.day)
        assertEquals(9, extraTable.startSection)
        assertEquals(10, extraTable.endSection)

        // 2. 列表视图 (#kblist_table) 回退解析
        val listOnlyHtml = """
            <table id="kblist_table">
              <tr>
                <td id="xq_1" rowspan="2">星期一</td>
                <td id="jc_1-5-6">5-6节</td>
                <td>
                  <div class="timetable_con">
                    <span class="title">计算机网络</span>
                    <p><span title="周次"></span>周次：1-10周</p>
                    <p><span title="上课地点"></span>计1-202</p>
                    <p><span title="教师"></span>刘老师</p>
                  </div>
                </td>
              </tr>
            </table>
        """.trimIndent()

        val listRes = CumtParsers.parseBkCourseHtml(listOnlyHtml)
        assertEquals(1, listRes.courses.size)
        assertEquals("计算机网络", listRes.courses[0].name)
        assertEquals(1, listRes.courses[0].day)
        assertEquals(5, listRes.courses[0].startSection)
        assertEquals(6, listRes.courses[0].endSection)
        assertEquals("计1-202", listRes.courses[0].position)
        assertEquals("刘老师", listRes.courses[0].teacher)
        assertEquals((1..10).toList(), listRes.courses[0].weeks)
    }

    @Test
    fun bkCourseScriptsAndFetchStateParseCorrectly() {
        val hookJs = CumtWebScripts.jwxtCourseAjaxHookJs()
        assertTrue(hookJs.contains("xskbcx_cxXsgrkb"))
        assertTrue(hookJs.contains("__cumtLastKbJson"))

        val fetchStartJs = CumtWebScripts.fetchBkCourseJsonStartJs()
        assertTrue(fetchStartJs.contains("xskbcx_cxXsgrkb.html"))
        assertTrue(fetchStartJs.contains("__cumtKbFetchState"))

        val domJs = CumtWebScripts.extractCourseDomHtmlJs(false)
        assertTrue(domJs.contains("table[id^=\"kbgrid_table_\"]"))
        assertTrue(domJs.contains("kblist_table"))

        val stateJson = """
            "{\"done\":true,\"json\":\"{\\\"kbList\\\":[]}\",\"xnm\":\"2026\",\"xqm\":\"3\",\"error\":\"\"}"
        """.trim()
        val state = CumtWebScripts.parseJwxtBkFetchState(stateJson)
        assertTrue(state.done)
        assertEquals("2026", state.xnm)
        assertEquals("3", state.xqm)
        assertTrue(state.json.contains("kbList"))
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

    @Test
    fun themeDensityForcesDefaultFontScale() {
        val scaled = Density(density = 3.0f, fontScale = 1.4f).withDefaultFontScale()
        assertEquals(3.0f, scaled.density)
        assertEquals(1.0f, scaled.fontScale)

        val small = Density(density = 2.5f, fontScale = 0.85f).withDefaultFontScale()
        assertEquals(2.5f, small.density)
        assertEquals(1.0f, small.fontScale)

        val defaultDensity = Density(density = 2.0f, fontScale = 1.0f)
        assertEquals(defaultDensity, defaultDensity.withDefaultFontScale())
    }

    @Test
    fun aiRecognizedCoursesParserHandlesLabScheduleAndSkipsIncompleteRows() {
        val sampleResponse = """
            ```json
            {
              "courses": [
                {
                  "name": "二阶系统的动态性能分析",
                  "teacher": "周晨",
                  "position": "信控-A205",
                  "day": 6,
                  "startSection": 1,
                  "endSection": 2,
                  "weeks": [11],
                  "remark": "第1次 · 第1批 · 2学时"
                },
                {
                  "name": "典型环节和系统频率特性的测量",
                  "teacher": "周晨",
                  "position": "信控-A205",
                  "day": "星期6",
                  "sections": "3-4",
                  "weeks": "11周",
                  "remark": "第1次 · 第1批 · 2学时"
                },
                {
                  "name": "基于MATLAB的现代控制理论分析与设计",
                  "teacher": "周晨",
                  "position": "信控-A205",
                  "day": "星期7",
                  "startSection": 1,
                  "endSection": 4,
                  "weeks": [12],
                  "remark": "第1次 · 第1批 · 4学时"
                },
                {
                  "name": "倒立摆的建模与控制",
                  "teacher": "周晨",
                  "position": "",
                  "day": null,
                  "startSection": null,
                  "endSection": null,
                  "weeks": []
                }
              ]
            }
            ```
        """.trimIndent()

        val parsed = CumtParsers.parseAiRecognizedCoursesJson(sampleResponse)
        assertEquals(3, parsed.size)

        assertEquals("二阶系统的动态性能分析", parsed[0].name)
        assertEquals("周晨", parsed[0].teacher)
        assertEquals("信控-A205", parsed[0].position)
        assertEquals(6, parsed[0].day)
        assertEquals(1, parsed[0].startSection)
        assertEquals(2, parsed[0].endSection)
        assertEquals(listOf(11), parsed[0].weeks)

        assertEquals(6, parsed[1].day)
        assertEquals(3, parsed[1].startSection)
        assertEquals(4, parsed[1].endSection)
        assertEquals(listOf(11), parsed[1].weeks)

        assertEquals(7, parsed[2].day)
        assertEquals(1, parsed[2].startSection)
        assertEquals(4, parsed[2].endSection)
        assertEquals(listOf(12), parsed[2].weeks)
    }

    @Test
    fun aiCourseServiceEndpointsPayloadsAndWeekHelpersWork() {
        assertEquals(
            "https://api.siliconflow.cn/v1/chat/completions",
            CumtAiCourseService.buildOpenAiChatEndpoint("https://api.siliconflow.cn/v1/")
        )
        assertEquals(
            "https://api.openai.com/v1/chat/completions",
            CumtAiCourseService.buildOpenAiChatEndpoint("https://api.openai.com/v1/chat/completions")
        )
        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent",
            CumtAiCourseService.buildGeminiEndpoint(
                "https://generativelanguage.googleapis.com/v1beta/",
                "gemini-2.5-flash"
            )
        )

        val openAiPayload = CumtAiCourseService.buildOpenAiVisionPayload(
            model = "Qwen/Qwen2.5-VL-72B-Instruct",
            mimeType = "image/jpeg",
            base64Image = "AAAA",
            userHint = "只识别红框里的"
        )
        assertTrue(openAiPayload.contains("Qwen/Qwen2.5-VL-72B-Instruct"))
        assertTrue(openAiPayload.contains("data:image/jpeg;base64,AAAA"))
        assertTrue(openAiPayload.contains("只识别红框里的"))

        val geminiPayload = CumtAiCourseService.buildGeminiVisionPayload(
            mimeType = "image/jpeg",
            base64Image = "BBBB",
            userHint = "第1批"
        )
        assertTrue(geminiPayload.contains("\"inlineData\""))
        assertTrue(geminiPayload.contains("\"data\":\"BBBB\""))
        assertTrue(geminiPayload.contains("第1批"))

        assertEquals(listOf(11, 12, 13, 15), CumtParsers.parseManualWeeksInput("11-13, 15"))
        assertEquals("11-13,15", CumtParsers.formatWeeksCompact(listOf(15, 11, 12, 13)))
        assertEquals("周六", CumtParsers.formatDayOfWeekLabel(6))
        assertEquals("周日", CumtParsers.formatDayOfWeekLabel(7))
    }

    @Test
    fun aiModelAutoDiscoveryParsesOpenAiAndGeminiAndFiltersNonChatModels() {
        assertEquals(
            "https://api.siliconflow.cn/v1/models",
            CumtAiCourseService.buildOpenAiModelsEndpoint("https://api.siliconflow.cn/v1/")
        )
        assertEquals(
            "https://api.openai.com/v1/models",
            CumtAiCourseService.buildOpenAiModelsEndpoint("https://api.openai.com/v1/chat/completions")
        )
        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/models?pageSize=100",
            CumtAiCourseService.buildGeminiModelsEndpoint("https://generativelanguage.googleapis.com/v1beta/")
        )

        val openAiModelsJson = """
            {
              "data": [
                {"id": "BAAI/bge-large-zh-v1.5"},
                {"id": "text-embedding-3-small"},
                {"id": "deepseek-ai/DeepSeek-R1"},
                {"id": "Qwen/Qwen2.5-VL-72B-Instruct"},
                {"id": "gpt-4o-mini"}
              ]
            }
        """.trimIndent()
        val openAiModels = CumtAiCourseService.parseOpenAiModelsJson(openAiModelsJson)
        assertEquals(3, openAiModels.size)
        // 视觉模型应排在前面
        assertTrue(openAiModels[0].supportsVision)
        assertTrue(openAiModels.any { it.id == "Qwen/Qwen2.5-VL-72B-Instruct" && it.supportsVision })
        assertTrue(openAiModels.any { it.id == "deepseek-ai/DeepSeek-R1" && it.isReasoning && !it.supportsVision })

        val geminiModelsJson = """
            {
              "models": [
                {
                  "name": "models/text-embedding-004",
                  "displayName": "Text Embedding 004",
                  "supportedGenerationMethods": ["embedContent"]
                },
                {
                  "name": "models/gemini-2.5-flash",
                  "displayName": "Gemini 2.5 Flash",
                  "supportedGenerationMethods": ["generateContent", "countTokens"]
                },
                {
                  "name": "models/gemini-2.5-pro",
                  "displayName": "Gemini 2.5 Pro",
                  "supportedGenerationMethods": ["generateContent"]
                }
              ]
            }
        """.trimIndent()
        val geminiModels = CumtAiCourseService.parseGeminiModelsJson(geminiModelsJson)
        assertEquals(2, geminiModels.size)
        assertEquals("gemini-2.5-flash", geminiModels[0].id)
        assertTrue(geminiModels.all { it.supportsVision && it.supportsTools })
    }

    @Test
    fun mcpToolDescriptorsAndClientConfigSnippetAreValid() {
        assertEquals(9, ScheduleToolEngine.TOOL_DESCRIPTORS.size)
        assertEquals(3, ScheduleToolEngine.RESOURCE_DESCRIPTORS.size)
        val writeTools = ScheduleToolEngine.TOOL_DESCRIPTORS.filter { it.isWrite }.map { it.name }.toSet()
        assertEquals(
            setOf("add_courses", "update_course", "reschedule_course", "delete_courses", "update_table_config"),
            writeTools
        )

        val snippet = ShiguangMcpDispatcher.buildClientConfigSnippet("192.168.1.8", 8765, "sg_test123")
        assertTrue(snippet.contains("\"url\": \"http://192.168.1.8:8765/mcp\""))
        assertTrue(snippet.contains("\"Authorization\": \"Bearer sg_test123\""))
        assertTrue(snippet.contains("\"shiguang-cumt-schedule\""))
    }

    @Test
    fun appUiFontScaleClampsSafelyAndIsolatesScheduleGridDefault() {
        val customLarge = Density(density = 3.0f, fontScale = 1.6f).withAppFontScale(1.15f)
        assertEquals(3.0f, customLarge.density)
        assertEquals(1.15f, customLarge.fontScale)

        // 超出上限与下限时应安全钳制到 [0.85, 1.25]
        assertEquals(1.25f, Density(2.0f, 1.0f).withAppFontScale(2.0f).fontScale)
        assertEquals(0.85f, Density(2.0f, 1.0f).withAppFontScale(0.5f).fontScale)

        // 课表网格内部调用 withDefaultFontScale() 应强制回到 1.0f，不受全局 UI 字号影响
        val isolatedGridDensity = customLarge.withDefaultFontScale()
        assertEquals(1.0f, isolatedGridDensity.fontScale)

        // 悬浮球图标风格解析
        assertEquals(CumtAiOrbIconStyle.BUBBLES, CumtAiOrbIconStyle.fromId(null))
        assertEquals(CumtAiOrbIconStyle.MASCOT, CumtAiOrbIconStyle.fromId("mascot"))
        assertEquals(CumtAiOrbIconStyle.CUSTOM, CumtAiOrbIconStyle.fromId("custom"))
    }
}
