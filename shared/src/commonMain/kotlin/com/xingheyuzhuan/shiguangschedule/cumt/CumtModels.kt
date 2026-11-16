package com.xingheyuzhuan.shiguangschedule.cumt

import kotlinx.serialization.Serializable

object CumtConstants {
    const val DESKTOP_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    // 统一身份认证 CAS
    const val CAS_CHECK_CAPTCHA_URL = "http://authserver.cumt.edu.cn/authserver/checkNeedCaptcha.htl"
    const val CAS_LOGIN_URL = "https://authserver.cumt.edu.cn/authserver/login"
    const val CAS_PORTAL_SERVICE = "http://portal.cumt.edu.cn/casservice"
    const val PORTAL_PROFILE_URL = "http://portal.cumt.edu.cn/portal/api/v2/infoplus/me/profile"
    const val CAS_LOGOUT_URL = "http://portal.cumt.edu.cn/portal/sso/logout"

    // 教务系统（本科 & 研究生）
    const val JWXT_LOGIN_CHECK_URL = "http://jwxt.cumt.edu.cn/jwglxt/xtgl/login_slogin.html"
    const val JWXT_SCHEDULE_URL =
        "http://jwxt.cumt.edu.cn/jwglxt/kbcx/xskbcx_cxXskbcxIndex.html?gnmkdm=N253508&layout=default"
    const val YJSXT_HOME_URL = "http://yjsxt.cumt.edu.cn/"
    const val JWXT_EXAM_URL =
        "http://jwxt.cumt.edu.cn/jwglxt/kwgl/kscx_cxXsksxxIndex.html?gnmkdm=N358105&layout=default"
    const val JWXT_SCORE_URL =
        "http://jwxt.cumt.edu.cn/jwglxt/cjcx/cjcx_cxDgXscj.html?gnmkdm=N305005&layout=default"
    const val JWXT_SCORE_DETAIL_URL =
        "http://jwxt.cumt.edu.cn/jwglxt/cjcx/cjcx_cxDgXsxmcj.html?gnmkdm=N305007&layout=default"

    // 图书馆
    const val LIB_CAS_LOGIN_URL =
        "https://authserver.cumt.edu.cn/authserver/login?service=http%3A%2F%2F121.248.104.188%3A8080%2FCASSSO%2Flogin.jsp"
    const val LIB_BASE_URL = "https://findcumt.libsp.com"
    const val LIB_WEB_OPAC_URL = "https://findcumt.libsp.com/"

    // 校园一卡通 & 宿舍电量
    const val YKT_CAS_LOGIN_URL =
        "https://yktm.cumt.edu.cn/berserker-auth/cas/login/wisedu?targetUrl=https%3A//yktm.cumt.edu.cn/plat-pc/%3Fname%3DloginTransit"
    const val YKT_BASE_URL = "https://yktm.cumt.edu.cn"
    const val YKT_RECHARGE_URL = "https://yktm.cumt.edu.cn/plat/dating"

    // 校历、校车与 VPN 指南
    const val SCHOOL_CALENDAR_URL = "https://www.cumt.edu.cn/21738/list.htm"
    const val SCHOOL_BUS_ANNOUNCEMENT_URL = "https://www.cumt.edu.cn/ggfw/list.htm"
    const val VPN_GUIDE_WEB_URL = "http://nic.cumt.edu.cn/info/1201/2407.htm"
    const val VPN_GUIDE_CLIENT_URL = "http://nic.cumt.edu.cn/info/1201/2408.htm"

    // 校园网 DR.COM
    const val CAMPUS_NET_PORTAL_URL = "http://10.2.5.251:801/eportal/"
    const val CAMPUS_NET_SELF_SERVICE_URL = "http://202.119.196.6:8080/Self/login/"
}

@Serializable
data class CumtAccountProfile(
    val username: String = "",
    val name: String = "",
    val phone: String = ""
)

data class CumtCaptchaCheck(
    val isNeedActive: Boolean = false,
    val isNeedCaptcha: Boolean = false
)

sealed interface CumtAuthResult {
    data class Success(val profile: CumtAccountProfile) : CumtAuthResult
    data class Error(val message: String, val needsAccountActivation: Boolean = false) : CumtAuthResult
}

@Serializable
data class CumtExamItem(
    val id: String,
    val courseName: String,
    val location: String = "",
    val dateTimeRaw: String = "",
    val year: Int,
    val month: Int,
    val day: Int,
    val isCustom: Boolean = false
)

@Serializable
data class CumtScoreItem(
    val id: String,
    val courseName: String,
    val credit: Double,
    val gpa: Double? = null,
    val scoreRaw: String,
    val examType: String = "正常考试",
    val isIncluded: Boolean = true
)

@Serializable
data class CumtScoreMappingEntry(
    val rawName: String,
    val numericScore: Double,
    val gpa: Double
)

object CumtDefaultScoreMappings {
    val entries: List<CumtScoreMappingEntry> = listOf(
        CumtScoreMappingEntry("免修", 100.0, 5.0),
        CumtScoreMappingEntry("优秀", 90.0, 4.5),
        CumtScoreMappingEntry("良好", 85.0, 3.5),
        CumtScoreMappingEntry("中等", 75.0, 2.5),
        CumtScoreMappingEntry("合格", 65.0, 1.0),
        CumtScoreMappingEntry("及格", 65.0, 1.0),
        CumtScoreMappingEntry("不合格", 0.0, 0.0),
        CumtScoreMappingEntry("不及格", 0.0, 0.0),
        CumtScoreMappingEntry("未评价", 0.0, 0.0),
        CumtScoreMappingEntry("旷考", 0.0, 0.0),
        CumtScoreMappingEntry("缓考", 0.0, 0.0),
        CumtScoreMappingEntry("免测", 0.0, 0.0)
    )
}

data class CumtScoreSummary(
    val weightedGpa: Double,
    val weightedScore: Double,
    val totalCredits: Double,
    val includedCount: Int
)

enum class ScoreSortOption(val displayName: String) {
    DEFAULT("默认排序"),
    NAME_ASC("名称 A → Z"),
    NAME_DESC("名称 Z → A"),
    SCORE_DESC("成绩由高到低"),
    SCORE_ASC("成绩由低到高"),
    CREDIT_DESC("学分由高到低"),
    CREDIT_ASC("学分由低到高")
}

@Serializable
data class CumtLoanBook(
    val loanId: String,
    val title: String,
    val author: String = "",
    val callNumber: String = "",
    val locationName: String = "",
    val loanDate: String = "",
    val normReturnDate: String = "",
    val renewCount: Int = 0,
    val canRenew: Boolean = true,
    val coverUrl: String? = null,
    val recordId: String = "",
    val isbn: String = ""
)

@Serializable
data class CumtLibrarySearchItem(
    val recordId: String,
    val title: String,
    val author: String = "",
    val publisher: String = "",
    val publishYear: String = "",
    val callNumber: String = "",
    val isbn: String = "",
    val availableCount: Int? = null,
    val totalCount: Int? = null,
    val coverUrl: String? = null
)

@Serializable
data class CumtCardTransaction(
    val description: String,
    val amountYuan: Double,
    val balanceAfterYuan: Double,
    val timeStr: String
)

@Serializable
data class CumtCampusCardInfo(
    val account: String = "",
    val balance: Double = 0.0,
    val updatedAtMillis: Long = 0L,
    val transactions: List<CumtCardTransaction> = emptyList()
)

enum class CumtDormBuilding(val id: String, val displayName: String) {
    YANMEI("14", "研梅"),
    XINGYUAN("13", "杏苑"),
    SONGZHU("12", "松竹"),
    TAOYUAN("11", "桃苑");

    companion object {
        fun fromId(id: String): CumtDormBuilding? = entries.firstOrNull { it.id == id }
    }
}

@Serializable
data class CumtDormPowerInfo(
    val buildingId: String = CumtDormBuilding.YANMEI.id,
    val buildingName: String = CumtDormBuilding.YANMEI.displayName,
    val roomId: String = "",
    val studentIdUsed: String = "",
    val account: String = "",
    val aid: String = "",
    val remainingPower: Double = 0.0,
    val maxPower: Double = 100.0,
    val updatedAtMillis: Long = 0L
)

enum class CampusNetCampus(val code: String, val displayName: String, val wlanAcName: String?) {
    NANHU("nh", "南湖校区", null),
    WENCHANG("wc", "文昌校区", "BRAS");

    companion object {
        fun fromCode(code: String): CampusNetCampus = entries.firstOrNull { it.code == code } ?: NANHU
    }
}

enum class CampusNetIsp(val id: String, val displayName: String, val suffixEncoded: String) {
    CUMT("cumt", "校园网", ""),
    TELECOM("telecom", "中国电信", "%40telecom"),
    UNICOM("unicom", "中国联通", "%40unicom"),
    CMCC("cmcc", "中国移动", "%40cmcc");

    companion object {
        fun fromId(id: String): CampusNetIsp = entries.firstOrNull { it.id == id } ?: CUMT
    }
}

@Serializable
data class CampusNetAccount(
    val id: String,
    val username: String,
    val encryptedPassword: String = "",
    val iv: String = "",
    val campusCode: String = CampusNetCampus.NANHU.code,
    val ispId: String = CampusNetIsp.CUMT.id,
    val remark: String = ""
)

data class CampusNetActionResult(
    val success: Boolean,
    val message: String,
    val alreadyLoggedIn: Boolean = false
)
