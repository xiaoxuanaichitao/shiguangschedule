package com.xingheyuzhuan.shiguangschedule.cumt

import kotlin.test.Test
import kotlin.test.assertEquals
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
                </tr>
              </tbody>
            </table>
        """.trimIndent()
        val exams = CumtParsers.parseExamHtml(examHtml)
        assertEquals(1, exams.size)
        assertEquals("数据结构", exams[0].courseName)
        assertEquals("博五B102", exams[0].location)
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
}
