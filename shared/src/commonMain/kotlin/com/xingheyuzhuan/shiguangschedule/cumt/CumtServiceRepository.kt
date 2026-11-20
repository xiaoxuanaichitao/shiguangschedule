package com.xingheyuzhuan.shiguangschedule.cumt

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import io.ktor.http.Url
import io.ktor.http.contentType
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.koin.core.annotation.Single
import kotlin.time.Clock

@Single
class CumtServiceRepository {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private fun createClient(
        cookieStorage: AcceptAllCookiesStorage = AcceptAllCookiesStorage(),
        followRedirects: Boolean = true,
        timeoutMs: Long = 15000L
    ): HttpClient {
        return HttpClient {
            this.followRedirects = followRedirects
            install(HttpCookies) {
                storage = cookieStorage
            }
            install(HttpTimeout) {
                requestTimeoutMillis = timeoutMs
                connectTimeoutMillis = 8000L
                socketTimeoutMillis = timeoutMs
            }
        }
    }

    private fun resolveRedirectUrl(baseUrl: String, location: String): String {
        if (location.startsWith("http://") || location.startsWith("https://")) return location
        val parsedBase = Url(baseUrl)
        val portPart = if (parsedBase.specifiedPort != 0 && parsedBase.specifiedPort != parsedBase.protocol.defaultPort) {
            ":${parsedBase.specifiedPort}"
        } else {
            ""
        }
        val origin = "${parsedBase.protocol.name}://${parsedBase.host}$portPart"
        return if (location.startsWith("/")) {
            "$origin$location"
        } else {
            val baseDir = parsedBase.encodedPath.substringBeforeLast('/', "")
            "$origin$baseDir/$location"
        }
    }

    private suspend fun followRedirectsManually(
        client: HttpClient,
        startUrl: String,
        maxHops: Int = 20
    ): Pair<HttpResponse, List<String>> {
        val visitedUrls = mutableListOf(startUrl)
        var currentUrl = startUrl
        var resp = client.get(currentUrl) {
            header(HttpHeaders.UserAgent, CumtConstants.DESKTOP_UA)
        }
        repeat(maxHops) {
            if (resp.status.value !in 300..399) return resp to visitedUrls
            val loc = resp.headers[HttpHeaders.Location]?.takeIf { it.isNotBlank() }
                ?: return resp to visitedUrls
            currentUrl = resolveRedirectUrl(currentUrl, loc)
            visitedUrls.add(currentUrl)
            resp = client.get(currentUrl) {
                header(HttpHeaders.UserAgent, CumtConstants.DESKTOP_UA)
            }
        }
        return resp to visitedUrls
    }

    private suspend fun casAuthenticateAndFollow(
        username: String,
        password: String,
        serviceUrl: String,
        maxHops: Int = 20
    ): Result<Pair<List<String>, HttpClient>> = withContext(Dispatchers.IO) {
        val client = createClient(followRedirects = false)
        runCatching {
            val loginPageUrl = "${CumtConstants.CAS_LOGIN_URL}?service=${serviceUrl.encodeURLParameter()}"
            val pageResp = client.get(loginPageUrl) {
                header(HttpHeaders.UserAgent, CumtConstants.DESKTOP_UA)
            }

            if (pageResp.status.value in 300..399) {
                val loc = pageResp.headers[HttpHeaders.Location].orEmpty()
                if (loc.isNotBlank()) {
                    val firstHop = resolveRedirectUrl(loginPageUrl, loc)
                    val (_, hopUrls) = followRedirectsManually(client, firstHop, maxHops = maxHops)
                    return@runCatching hopUrls to client
                }
            }

            val html = pageResp.bodyAsText()
            val inputs = CumtParsers.parseCasLoginInputs(html)
                ?: error("无法解析统一身份认证登录表单，请检查校园网或认证服务状态")

            val encryptedPassword = CumtAesCrypto.encryptPassword(password, inputs.pwdEncryptSalt)

            var postResp: HttpResponse = client.submitForm(
                url = loginPageUrl,
                formParameters = Parameters.build {
                    append("username", username)
                    append("password", encryptedPassword)
                    append("_eventId", "submit")
                    append("cllt", "userNameLogin")
                    append("execution", inputs.execution)
                }
            ) {
                header(HttpHeaders.UserAgent, CumtConstants.DESKTOP_UA)
                header(HttpHeaders.Origin, "https://authserver.cumt.edu.cn")
                header(HttpHeaders.Referrer, loginPageUrl)
            }

            // 处理并发登录提示（X-Frame-Options: DENY）
            if (postResp.status.value == 200 && postResp.headers["X-Frame-Options"] == "DENY") {
                val conflictExec = CumtParsers.parseCasConflictExecution(postResp.bodyAsText())
                if (conflictExec != null) {
                    postResp = client.submitForm(
                        url = loginPageUrl,
                        formParameters = Parameters.build {
                            append("_eventId", "continue")
                            append("execution", conflictExec)
                        }
                    ) {
                        header(HttpHeaders.UserAgent, CumtConstants.DESKTOP_UA)
                        header(HttpHeaders.Origin, "https://authserver.cumt.edu.cn")
                        header(HttpHeaders.Referrer, loginPageUrl)
                    }
                }
            }

            if (postResp.status.value == 401) {
                val tip = CumtParsers.parseCasError(postResp.bodyAsText())
                error(tip ?: "用户名或密码错误")
            }

            val firstLocation = postResp.headers[HttpHeaders.Location]
            if (firstLocation.isNullOrBlank()) {
                val tip = CumtParsers.parseCasError(postResp.bodyAsText())
                error(tip ?: "统一身份认证未返回跳转地址 (HTTP ${postResp.status.value})")
            }

            if (firstLocation.contains("improveInfo")) {
                error("当前账号密码过于简单或存在安全提示，请先用浏览器访问统一身份认证平台完成修改")
            }

            val firstHopUrl = resolveRedirectUrl(loginPageUrl, firstLocation)
            val (_, visitedLocations) = followRedirectsManually(client, firstHopUrl, maxHops = maxHops)
            visitedLocations to client
        }.onFailure {
            client.close()
        }
    }

    // ==================== 统一身份认证 & 校内网络检测 ====================

    suspend fun checkNeedCaptcha(username: String): Result<CumtCaptchaCheck> = withContext(Dispatchers.IO) {
        runCatching {
            val client = createClient(timeoutMs = 8000L)
            try {
                val resp = client.get("${CumtConstants.CAS_CHECK_CAPTCHA_URL}?username=${username.encodeURLParameter()}") {
                    header(HttpHeaders.UserAgent, CumtConstants.DESKTOP_UA)
                }
                val obj = json.parseToJsonElement(resp.bodyAsText()).jsonObject
                CumtCaptchaCheck(
                    isNeedActive = obj["isNeedActive"]?.jsonPrimitive?.contentOrNull == "true",
                    isNeedCaptcha = obj["isNeed"]?.jsonPrimitive?.contentOrNull == "true"
                )
            } finally {
                client.close()
            }
        }
    }

    suspend fun checkIntranet(): Boolean = withContext(Dispatchers.IO) {
        val client = createClient(timeoutMs = 4000L)
        try {
            val resp = client.get(CumtConstants.JWXT_LOGIN_CHECK_URL) {
                header(HttpHeaders.UserAgent, CumtConstants.DESKTOP_UA)
            }
            resp.status.value in 200..399
        } catch (_: Exception) {
            false
        } finally {
            client.close()
        }
    }

    suspend fun loginCas(username: String, password: String): CumtAuthResult = withContext(Dispatchers.IO) {
        val trimmedUser = username.trim()
        if (trimmedUser.isEmpty() || password.isEmpty()) {
            return@withContext CumtAuthResult.Error("请输入学号和密码")
        }

        // 先检测账号是否未激活或需要验证码
        val check = checkNeedCaptcha(trimmedUser).getOrNull()
        if (check?.isNeedActive == true) {
            return@withContext CumtAuthResult.Error(
                message = "该账号尚未激活统一身份认证，请先前往认证页面激活账号",
                needsAccountActivation = true
            )
        }
        if (check?.isNeedCaptcha == true) {
            return@withContext CumtAuthResult.Error("当前账号连续尝试次数较多，需先在浏览器或教务登录页输入验证码后再绑定")
        }

        val authAttempt = casAuthenticateAndFollow(
            username = trimmedUser,
            password = password,
            serviceUrl = CumtConstants.CAS_PORTAL_SERVICE
        )

        authAttempt.fold(
            onSuccess = { (_, client) ->
                try {
                    var name = ""
                    var phone = ""
                    runCatching {
                        val profileResp = client.get(CumtConstants.PORTAL_PROFILE_URL) {
                            header(HttpHeaders.UserAgent, CumtConstants.DESKTOP_UA)
                        }
                        val root = json.parseToJsonElement(profileResp.bodyAsText()).jsonObject
                        val entity = root["results"]?.jsonObject
                            ?.get("entities")?.jsonArray
                            ?.firstOrNull()?.jsonObject
                        name = entity?.get("name")?.jsonPrimitive?.contentOrNull.orEmpty()
                        phone = entity?.get("phone")?.jsonPrimitive?.contentOrNull.orEmpty()
                    }
                    CumtAuthResult.Success(
                        CumtAccountProfile(username = trimmedUser, name = name, phone = phone)
                    )
                } finally {
                    client.close()
                }
            },
            onFailure = { e ->
                CumtAuthResult.Error(e.message ?: "统一身份认证登录失败")
            }
        )
    }

    suspend fun logoutCas() = withContext(Dispatchers.IO) {
        val client = createClient(timeoutMs = 6000L)
        try {
            runCatching { client.get(CumtConstants.CAS_LOGOUT_URL) }
        } finally {
            client.close()
        }
    }

    // ==================== 校园一卡通 & 宿舍电量鉴权 ====================

    private suspend fun authenticateYktSession(username: String, password: String): Pair<String, HttpClient> {
        val (locations, client) = casAuthenticateAndFollow(
            username = username,
            password = password,
            serviceUrl = CumtConstants.YKT_CAS_LOGIN_URL,
            maxHops = 20
        ).getOrThrow()

        val synjonesAuth = CumtParsers.extractSynjonesAuthToken(locations)
        if (synjonesAuth.isNullOrBlank()) {
            client.close()
            error("未能获取一卡通认证令牌 (synjones-auth)")
        }
        return synjonesAuth to client
    }

    // ==================== 校园一卡通 ====================

    suspend fun fetchCampusCardInfo(username: String, password: String): Result<CumtCampusCardInfo> =
        withContext(Dispatchers.IO) {
            runCatching {
                val (synjonesAuth, client) = authenticateYktSession(username, password)

                try {
                    val cardResp = client.get("${CumtConstants.YKT_BASE_URL}/berserker-app/ykt/tsm/getCampusCards?synAccessSource=pc") {
                        header("Synjones-Auth", "bearer $synjonesAuth")
                        header(HttpHeaders.UserAgent, CumtConstants.DESKTOP_UA)
                    }.bodyAsText()

                    val cardRoot = json.parseToJsonElement(cardResp).jsonObject
                    val firstCard = cardRoot["data"]?.jsonObject
                        ?.get("card")?.jsonArray
                        ?.firstOrNull()?.jsonObject
                        ?: error("未查询到校园卡信息")

                    val account = firstCard["account"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val elecAmt = firstCard["elec_accamt"]?.jsonPrimitive?.doubleOrNull
                        ?: firstCard["db_balance"]?.jsonPrimitive?.doubleOrNull
                        ?: 0.0
                    val balanceYuan = elecAmt / 100.0

                    val turnoverResp = client.get(
                        "${CumtConstants.YKT_BASE_URL}/berserker-search/search/personal/turnover?size=20&current=1&synAccessSource=pc"
                    ) {
                        header("Synjones-Auth", "bearer $synjonesAuth")
                        header(HttpHeaders.UserAgent, CumtConstants.DESKTOP_UA)
                    }.bodyAsText()

                    val records = json.parseToJsonElement(turnoverResp).jsonObject["data"]
                        ?.jsonObject?.get("records")?.jsonArray
                        ?.mapNotNull { el ->
                            val r = el.jsonObject
                            val desc = r["resume"]?.jsonPrimitive?.contentOrNull.orEmpty()
                            val icon = r["icon"]?.jsonPrimitive?.contentOrNull.orEmpty()
                            val tranAmt = (r["tranamt"]?.jsonPrimitive?.doubleOrNull ?: 0.0) / 100.0
                            val signedAmt = if (icon == "consume") -kotlin.math.abs(tranAmt) else kotlin.math.abs(tranAmt)
                            val cardBalance = (r["cardBalance"]?.jsonPrimitive?.doubleOrNull ?: 0.0) / 100.0
                            val timeStr = r["jndatetimeStr"]?.jsonPrimitive?.contentOrNull.orEmpty()
                            CumtCardTransaction(
                                description = desc.ifBlank { "交易记录" },
                                amountYuan = signedAmt,
                                balanceAfterYuan = cardBalance,
                                timeStr = timeStr
                            )
                        }.orEmpty()

                    CumtCampusCardInfo(
                        account = account,
                        balance = balanceYuan,
                        updatedAtMillis = Clock.System.now().toEpochMilliseconds(),
                        transactions = records
                    )
                } finally {
                    client.close()
                }
            }
        }

    // ==================== 宿舍电量 ====================

    suspend fun queryDormPower(
        casUsername: String,
        casPassword: String,
        campus: CumtDormCampus,
        building: CumtDormBuildingItem,
        roomId: String,
        queryStudentId: String = casUsername
    ): Result<CumtDormPowerInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanCasUser = casUsername.trim()
            val targetSno = queryStudentId.trim().ifBlank { cleanCasUser }
            val cleanRoomId = roomId.trim()
            require(cleanCasUser.isNotEmpty() && casPassword.isNotEmpty()) {
                "请先在「统一身份认证」绑定矿大账号后再查询宿舍电量"
            }
            require(cleanRoomId.isNotEmpty()) { "请输入宿舍号（${campus.roomHint}）" }

            val (synjonesAuth, client) = authenticateYktSession(cleanCasUser, casPassword)
            try {
                // Step 1: 打开电费服务应用，激活 yktm web H5 Cookie 会话
                val appRedirectUrl =
                    "${CumtConstants.YKT_ELEC_APP_REDIRECT_URL}?type=app&appId=38" +
                        "&synjones-auth=${synjonesAuth.encodeURLParameter()}" +
                        "&source=h5&synAccessSource=h5&loginFrom=h5"
                followRedirectsManually(client, appRedirectUrl, maxHops = 20)

                // Step 2: 查询学号对应的校园卡 account
                val cardJsonData = buildJsonObject {
                    put("query_card", buildJsonObject {
                        put("id", targetSno)
                        put("idtype", "sno")
                    })
                }.toString()
                val step2Resp = client.submitForm(
                    url = "${CumtConstants.YKT_BASE_URL}/web/Common/Card.html",
                    formParameters = Parameters.build {
                        append("funname", "synjones.onecard.query.card")
                        append("json", "true")
                        append("jsondata", cardJsonData)
                    }
                ) {
                    header(HttpHeaders.UserAgent, CumtConstants.DESKTOP_UA)
                }.bodyAsText()

                var account = runCatching {
                    json.parseToJsonElement(step2Resp).jsonObject["query_card"]
                        ?.jsonObject?.get("card")?.jsonArray
                        ?.firstOrNull()?.jsonObject?.get("account")?.jsonPrimitive?.contentOrNull
                }.getOrNull()?.takeIf { it.isNotBlank() }

                // 若 Card.html 未返回且查询的是本人学号，降级使用 getCampusCards 接口提取 account
                if (account == null && targetSno == cleanCasUser) {
                    val cardsResp = client.get("${CumtConstants.YKT_BASE_URL}/berserker-app/ykt/tsm/getCampusCards?synAccessSource=pc") {
                        header("Synjones-Auth", "bearer $synjonesAuth")
                        header(HttpHeaders.UserAgent, CumtConstants.DESKTOP_UA)
                    }.bodyAsText()
                    account = runCatching {
                        json.parseToJsonElement(cardsResp).jsonObject["data"]
                            ?.jsonObject?.get("card")?.jsonArray
                            ?.firstOrNull()?.jsonObject?.get("account")?.jsonPrimitive?.contentOrNull
                    }.getOrNull()?.takeIf { it.isNotBlank() }
                }
                val resolvedAccount = account ?: error("未查询到学号 $targetSno 对应的校园卡账户")

                // Step 3: 获取可用电费应用 aid 列表并匹配校区 systemAid
                val appJsonData = buildJsonObject {
                    put("query_applist", buildJsonObject {
                        put("apptype", "elec")
                    })
                }.toString()
                val step3Resp = client.submitForm(
                    url = "${CumtConstants.YKT_BASE_URL}/web/NetWork/AppList.html",
                    formParameters = Parameters.build {
                        append("funname", "synjones.onecard.query.applist")
                        append("json", "true")
                        append("jsondata", appJsonData)
                    }
                ) {
                    header(HttpHeaders.UserAgent, CumtConstants.DESKTOP_UA)
                }.bodyAsText()
                val availableAids = runCatching {
                    json.parseToJsonElement(step3Resp).jsonObject["query_applist"]
                        ?.jsonObject?.get("applist")?.jsonArray
                        ?.mapNotNull { it.jsonObject["aid"]?.jsonPrimitive?.contentOrNull }
                        .orEmpty()
                }.getOrDefault(emptyList())
                val resolvedAid = CumtParsers.resolveDormElecAid(campus, availableAids)
                if (resolvedAid.isBlank()) {
                    error("未能获取 ${campus.label} 的电费服务 ID")
                }

                // Step 4: 查询房间剩余电量
                val roomJsonData = CumtParsers.buildDormRoomInfoJson(
                    account = resolvedAccount,
                    aid = resolvedAid,
                    campus = campus,
                    building = building,
                    roomId = cleanRoomId
                )
                val step4Resp = client.submitForm(
                    url = "${CumtConstants.YKT_BASE_URL}/web/Common/Tsm.html",
                    formParameters = Parameters.build {
                        append("funname", "synjones.onecard.query.elec.roominfo")
                        append("json", "true")
                        append("jsondata", roomJsonData)
                    }
                ) {
                    header(HttpHeaders.UserAgent, CumtConstants.DESKTOP_UA)
                }.bodyAsText()

                val errmsg = json.parseToJsonElement(step4Resp).jsonObject["query_elec_roominfo"]
                    ?.jsonObject?.get("errmsg")?.jsonPrimitive?.contentOrNull.orEmpty()
                val kwh = CumtParsers.parseDormPowerMessage(errmsg)
                    ?: error(
                        errmsg.ifBlank {
                            "未查询到 ${campus.label} ${building.name} $cleanRoomId 的电量信息（格式参考：${campus.roomHint}）"
                        }
                    )

                CumtDormPowerInfo(
                    campusId = campus.id,
                    campusName = campus.label,
                    buildingId = building.id,
                    buildingName = building.name,
                    roomId = cleanRoomId,
                    studentIdUsed = targetSno,
                    account = resolvedAccount,
                    aid = resolvedAid,
                    remainingPower = kwh,
                    maxPower = if (kwh > 100.0) ((kwh / 50.0).toInt() + 1) * 50.0 else 100.0,
                    updatedAtMillis = Clock.System.now().toEpochMilliseconds()
                )
            } finally {
                client.close()
            }
        }
    }

    // ==================== 图书馆 ====================

    suspend fun loginLibraryToken(username: String, password: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val (locations, client) = casAuthenticateAndFollow(
                    username = username,
                    password = password,
                    serviceUrl = "http://121.248.104.188:8080/CASSSO/login.jsp"
                ).getOrThrow()

                try {
                    // FlyingKxz 原规则: 第 3 个跳转 URL 的 '/' 分割第 5 段即为 jwtOpacAuth
                    val candidate = locations.firstNotNullOfOrNull { url ->
                        extractLibJwtFromUrl(url)
                    }
                    candidate ?: error("未能从图书馆认证链接获取授权令牌")
                } finally {
                    client.close()
                }
            }
        }

    private fun extractLibJwtFromUrl(url: String): String? {
        if (!url.contains("findcumt.libsp.com")) return null
        val queryToken = Regex("""(?:jwtOpacAuth|token)=([^&#]+)""").find(url)?.groupValues?.get(1)
        if (!queryToken.isNullOrBlank()) return queryToken
        // FlyingKxz 原规则：未替换 `#/` 前的 URL按 '/' 分割第 5 段即令牌（如 https://findcumt.libsp.com/#/casLogin/<jwt>）
        val rawParts = url.split('/')
        val fifth = rawParts.getOrNull(5)?.substringBefore('?')?.substringBefore('#')
        if (!fifth.isNullOrBlank() && fifth.length >= 16) return fifth
        val jwtCandidate = rawParts
            .map { it.substringBefore('?').substringBefore('#') }
            .lastOrNull { it.startsWith("eyJ") && it.length > 20 }
        return jwtCandidate ?: rawParts
            .map { it.substringBefore('?').substringBefore('#') }
            .lastOrNull { it.length >= 24 }
    }

    suspend fun fetchLibraryCurrentLoans(jwtToken: String): Result<List<CumtLoanBook>> =
        fetchLoanListInternal(
            jwtToken = jwtToken,
            endpoint = "${CumtConstants.LIB_BASE_URL}/find/loanInfo/loanList",
            isHistory = false
        )

    suspend fun fetchLibraryLoanHistory(jwtToken: String): Result<List<CumtLoanBook>> =
        fetchLoanListInternal(
            jwtToken = jwtToken,
            endpoint = "${CumtConstants.LIB_BASE_URL}/find/loanInfo/loanHistoryList",
            isHistory = true
        )

    private suspend fun fetchLoanListInternal(
        jwtToken: String,
        endpoint: String,
        isHistory: Boolean
    ): Result<List<CumtLoanBook>> = withContext(Dispatchers.IO) {
        runCatching {
            val client = createClient(timeoutMs = 15000L)
            try {
                val bodyJson = buildJsonObject {
                    put("page", 1)
                    put("rows", 50)
                    put("searchType", 1)
                    put("searchContent", "")
                }.toString()

                val resp = client.post(endpoint) {
                    header("jwtOpacAuth", jwtToken)
                    header(HttpHeaders.UserAgent, CumtConstants.DESKTOP_UA)
                    contentType(ContentType.Application.Json)
                    setBody(bodyJson)
                }
                if (resp.status.value == 401) {
                    error("LIBRARY_TOKEN_EXPIRED")
                }

                val root = json.parseToJsonElement(resp.bodyAsText()).jsonObject
                if (root["success"]?.jsonPrimitive?.contentOrNull == "false") {
                    val err = root["message"]?.jsonPrimitive?.contentOrNull ?: "图书馆会话已过期"
                    error(err)
                }
                val list = root["data"]?.jsonObject?.get("searchResult")?.jsonArray
                    ?: root["data"]?.jsonObject?.get("list")?.jsonArray
                    ?: return@runCatching emptyList()

                val books = list.map { el ->
                    val obj = el.jsonObject
                    val isbn = obj["isbn"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val title = obj["title"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val recordId = obj["recordId"]?.jsonPrimitive?.contentOrNull
                        ?: obj["recId"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    CumtLoanBook(
                        loanId = obj["loanId"]?.jsonPrimitive?.contentOrNull
                            ?: obj["id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        title = title.ifBlank { "未知书目" },
                        author = obj["author"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        callNumber = obj["callNo"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        locationName = obj["locationName"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        loanDate = obj["loanDate"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        normReturnDate = obj["normReturnDate"]?.jsonPrimitive?.contentOrNull
                            ?: obj["returnDate"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        renewCount = obj["renewCount"]?.jsonPrimitive?.intOrNull ?: 0,
                        canRenew = !isHistory && (obj["renewCount"]?.jsonPrimitive?.intOrNull ?: 0) < 2,
                        recordId = recordId,
                        isbn = isbn
                    )
                }

                // 并发拉取封面（仅对前 20 本或有 ISBN 的记录，失败不影响列表展示）
                if (!isHistory && books.isNotEmpty()) {
                    coroutineScope {
                        books.mapIndexed { idx, book ->
                            async {
                                if (idx < 20 && (book.isbn.isNotBlank() || book.recordId.isNotBlank())) {
                                    val cover = fetchBookCoverUrl(client, jwtToken, book.isbn, book.title, book.recordId)
                                    book.copy(coverUrl = cover)
                                } else {
                                    book
                                }
                            }
                        }.awaitAll()
                    }
                } else {
                    books
                }
            } finally {
                client.close()
            }
        }
    }

    private suspend fun fetchBookCoverUrl(
        client: HttpClient,
        jwtToken: String,
        isbn: String,
        title: String,
        recordId: String
    ): String? {
        return runCatching {
            val url = "${CumtConstants.LIB_BASE_URL}/find/book/getDuxiuImageUrl" +
                "?isbn=${isbn.encodeURLParameter()}&title=${title.encodeURLParameter()}&recordId=${recordId.encodeURLParameter()}"
            val resp = client.get(url) {
                header("jwtOpacAuth", jwtToken)
                header(HttpHeaders.UserAgent, CumtConstants.DESKTOP_UA)
            }.bodyAsText()
            val data = json.parseToJsonElement(resp).jsonObject["data"]?.jsonPrimitive?.contentOrNull
            data?.takeIf { it.startsWith("http") }
        }.getOrNull()
    }

    suspend fun renewLibraryBook(jwtToken: String, loanId: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val client = createClient(timeoutMs = 15000L)
                try {
                    val bodyJson = buildJsonObject {
                        putJsonArray("loanIds") { add(kotlinx.serialization.json.JsonPrimitive(loanId)) }
                    }.toString()
                    val resp = client.post("${CumtConstants.LIB_BASE_URL}/find/lendbook/reNew") {
                        header("jwtOpacAuth", jwtToken)
                        header(HttpHeaders.UserAgent, CumtConstants.DESKTOP_UA)
                        contentType(ContentType.Application.Json)
                        setBody(bodyJson)
                    }.bodyAsText()
                    val root = json.parseToJsonElement(resp).jsonObject
                    val msg = root["message"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val ok = root["success"]?.jsonPrimitive?.contentOrNull == "true"
                    if (!ok) {
                        error(msg.ifBlank { "续借失败" })
                    }
                    msg.ifBlank { "续借成功" }
                } finally {
                    client.close()
                }
            }
        }

    suspend fun searchLibraryCatalog(
        keyword: String,
        page: Int = 1,
        jwtToken: String? = null
    ): Result<List<CumtLibrarySearchItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanKw = keyword.trim()
            if (cleanKw.isEmpty()) return@runCatching emptyList()

            val client = createClient(timeoutMs = 15000L)
            try {
                val bodyJson = buildJsonObject {
                    put("searchFieldContent", cleanKw)
                    put("searchField", "keyWord")
                    put("page", page)
                    put("rows", 20)
                    put("sortType", "desc")
                    put("sortField", "relevance")
                    put("matchMode", "2")
                    putJsonArray("resourceType") {}
                    putJsonArray("subject") {}
                    putJsonArray("disCode") {}
                    putJsonArray("publisher") {}
                    putJsonArray("libCode") {}
                    putJsonArray("locationId") {}
                    putJsonArray("eCollectionIds") {}
                    putJsonArray("curLocationId") {}
                    putJsonArray("campusId") {}
                    putJsonArray("kindNo") {}
                    putJsonArray("collectionName") {}
                    putJsonArray("author") {}
                    putJsonArray("langCode") {}
                    putJsonArray("countryCode") {}
                    putJsonArray("coreInclude") {}
                    putJsonArray("ddType") {}
                    putJsonArray("verifyStatus") {}
                    putJsonArray("group") {}
                    putJsonArray("keyWord") {}
                }.toString()

                val resp = client.post("${CumtConstants.LIB_BASE_URL}/find/unify/search") {
                    if (!jwtToken.isNullOrBlank()) {
                        header("jwtOpacAuth", jwtToken)
                    }
                    header(HttpHeaders.UserAgent, CumtConstants.DESKTOP_UA)
                    contentType(ContentType.Application.Json)
                    setBody(bodyJson)
                }.bodyAsText()

                val root = json.parseToJsonElement(resp).jsonObject
                if (root["success"]?.jsonPrimitive?.contentOrNull == "false") {
                    error(root["message"]?.jsonPrimitive?.contentOrNull ?: "馆藏检索返回异常")
                }

                val resultArray = root["data"]?.jsonObject?.get("searchResult")?.jsonArray
                    ?: return@runCatching emptyList()

                resultArray.map { el ->
                    val o: JsonObject = el.jsonObject
                    CumtLibrarySearchItem(
                        recordId = o["recordId"]?.jsonPrimitive?.contentOrNull
                            ?: o["recId"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        title = o["title"]?.jsonPrimitive?.contentOrNull.orEmpty().ifBlank { "未知书名" },
                        author = o["author"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        publisher = o["publisher"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        publishYear = o["publishYear"]?.jsonPrimitive?.contentOrNull
                            ?: o["pubYear"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        callNumber = o["callNo"]?.jsonPrimitive?.contentOrNull
                            ?: o["callnoOne"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        isbn = o["isbn"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        availableCount = o["onShelfCount"]?.jsonPrimitive?.intOrNull
                            ?: o["availableCount"]?.jsonPrimitive?.intOrNull,
                        totalCount = o["physicalCount"]?.jsonPrimitive?.intOrNull
                            ?: o["totalCount"]?.jsonPrimitive?.intOrNull
                    )
                }
            } finally {
                client.close()
            }
        }
    }

    // ==================== 校园网 DR.COM ====================

    suspend fun loginCampusNet(
        username: String,
        password: String,
        campus: CampusNetCampus,
        isp: CampusNetIsp
    ): CampusNetActionResult = withContext(Dispatchers.IO) {
        val cleanUser = username.trim()
        if (cleanUser.isEmpty() || password.isEmpty()) {
            return@withContext CampusNetActionResult(false, "请输入校园网账号和密码")
        }
        val client = createClient(timeoutMs = 6000L)
        try {
            val brasParam = campus.wlanAcName?.let { "&wlan_ac_name=$it" } ?: ""
            val url = "${CumtConstants.CAMPUS_NET_PORTAL_URL}?c=Portal&a=login&login_method=1" +
                "&user_account=${cleanUser.encodeURLParameter()}${isp.suffixEncoded}$brasParam" +
                "&user_password=${password.encodeURLParameter()}"
            val body = client.get(url) {
                header(HttpHeaders.UserAgent, CumtConstants.DESKTOP_UA)
            }.bodyAsText()
            CumtParsers.parseCampusNetJsonp(body)
        } catch (e: Exception) {
            CampusNetActionResult(false, "校园网连接失败（请确认已接入 CUMT Wi-Fi）：${e.message ?: "网络不可达"}")
        } finally {
            client.close()
        }
    }

    suspend fun logoutCampusNet(campus: CampusNetCampus): CampusNetActionResult = withContext(Dispatchers.IO) {
        val client = createClient(timeoutMs = 6000L)
        try {
            val brasParam = campus.wlanAcName?.let { "&wlan_ac_name=$it" } ?: ""
            val url = "${CumtConstants.CAMPUS_NET_PORTAL_URL}?c=Portal&a=logout&login_method=1$brasParam"
            val body = client.get(url) {
                header(HttpHeaders.UserAgent, CumtConstants.DESKTOP_UA)
            }.bodyAsText()
            CumtParsers.parseCampusNetJsonp(body)
        } catch (e: Exception) {
            CampusNetActionResult(false, "校园网注销请求失败：${e.message ?: "网络不可达"}")
        } finally {
            client.close()
        }
    }
}
