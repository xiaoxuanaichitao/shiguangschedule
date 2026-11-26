package com.xingheyuzhuan.shiguangschedule.cumt

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.xingheyuzhuan.shiguangschedule.tool.SecureCrypto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single

@Single
class CumtLocalRepository(
    @Named("CumtData") private val dataStore: DataStore<Preferences>,
    private val secureCrypto: SecureCrypto
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private companion object {
        val KEY_CAS_USERNAME = stringPreferencesKey("cumt_cas_username")
        val KEY_CAS_ENC_PASSWORD = stringPreferencesKey("cumt_cas_enc_password")
        val KEY_CAS_PASSWORD_IV = stringPreferencesKey("cumt_cas_password_iv")
        val KEY_CAS_PLAIN_FALLBACK = stringPreferencesKey("cumt_cas_plain_fallback")
        val KEY_CAS_NAME = stringPreferencesKey("cumt_cas_name")
        val KEY_CAS_PHONE = stringPreferencesKey("cumt_cas_phone")

        val KEY_EXAMS_JSON = stringPreferencesKey("cumt_exams_json")
        val KEY_SCORES_JSON = stringPreferencesKey("cumt_scores_json")
        val KEY_SCORE_MAPPINGS_JSON = stringPreferencesKey("cumt_score_mappings_json")
        val KEY_SCORE_RATE = doublePreferencesKey("cumt_score_rate")

        val KEY_CARD_CACHE_JSON = stringPreferencesKey("cumt_card_cache_json")
        val KEY_DORM_POWER_JSON = stringPreferencesKey("cumt_dorm_power_json")

        val KEY_NET_ACCOUNTS_JSON = stringPreferencesKey("cumt_net_accounts_json")
        val KEY_NET_AUTO_LOGIN = booleanPreferencesKey("cumt_net_auto_login")
        val KEY_NET_LAST_CAMPUS = stringPreferencesKey("cumt_net_last_campus")
        val KEY_NET_LAST_ISP = stringPreferencesKey("cumt_net_last_isp")

        val KEY_AI_PROVIDER_ID = stringPreferencesKey("cumt_ai_provider_id")
        val KEY_AI_BASE_URL = stringPreferencesKey("cumt_ai_base_url")
        val KEY_AI_MODEL = stringPreferencesKey("cumt_ai_model")
        val KEY_AI_PROTOCOL = stringPreferencesKey("cumt_ai_protocol")
        val KEY_AI_ENC_API_KEY = stringPreferencesKey("cumt_ai_enc_api_key")
        val KEY_AI_API_KEY_IV = stringPreferencesKey("cumt_ai_api_key_iv")
        val KEY_AI_PLAIN_FALLBACK = stringPreferencesKey("cumt_ai_plain_fallback")
        val KEY_AI_CACHED_MODELS_JSON = stringPreferencesKey("cumt_ai_cached_models_json")
        val KEY_AI_ORB_ENABLED = booleanPreferencesKey("cumt_ai_orb_enabled")
        val KEY_AI_ORB_Y_FRACTION = floatPreferencesKey("cumt_ai_orb_y_fraction")
        val KEY_AI_ORB_ON_RIGHT = booleanPreferencesKey("cumt_ai_orb_on_right")
        val KEY_AI_AUTO_EXEC_WRITES = booleanPreferencesKey("cumt_ai_auto_exec_writes")

        val KEY_MCP_ENABLED = booleanPreferencesKey("cumt_mcp_enabled")
        val KEY_MCP_PORT = intPreferencesKey("cumt_mcp_port")
        val KEY_MCP_TOKEN = stringPreferencesKey("cumt_mcp_token")
        val KEY_MCP_ALLOW_WRITE = booleanPreferencesKey("cumt_mcp_allow_write")
        val KEY_MCP_BIND_ALL = booleanPreferencesKey("cumt_mcp_bind_all")
    }

    // ==================== 统一身份认证凭据 ====================

    val profileFlow: Flow<CumtAccountProfile> = dataStore.data.map { prefs ->
        CumtAccountProfile(
            username = prefs[KEY_CAS_USERNAME].orEmpty(),
            name = prefs[KEY_CAS_NAME].orEmpty(),
            phone = prefs[KEY_CAS_PHONE].orEmpty()
        )
    }

    suspend fun getSavedCredentials(): Pair<String, String>? {
        val prefs = dataStore.data.first()
        val username = prefs[KEY_CAS_USERNAME].orEmpty()
        if (username.isBlank()) return null
        val enc = prefs[KEY_CAS_ENC_PASSWORD].orEmpty()
        val iv = prefs[KEY_CAS_PASSWORD_IV].orEmpty()
        val password = if (enc.isNotEmpty() && iv.isNotEmpty()) {
            secureCrypto.decrypt(enc, iv)
        } else {
            null
        } ?: prefs[KEY_CAS_PLAIN_FALLBACK]?.takeIf { it.isNotEmpty() }
        return if (!password.isNullOrEmpty()) username to password else null
    }

    suspend fun saveCredentials(
        username: String,
        password: String,
        profile: CumtAccountProfile
    ) {
        val crypto = secureCrypto.encrypt(password)
        dataStore.edit { prefs ->
            prefs[KEY_CAS_USERNAME] = username.trim()
            if (crypto != null) {
                prefs[KEY_CAS_ENC_PASSWORD] = crypto.encryptedData
                prefs[KEY_CAS_PASSWORD_IV] = crypto.iv
                prefs.remove(KEY_CAS_PLAIN_FALLBACK)
            } else {
                prefs.remove(KEY_CAS_ENC_PASSWORD)
                prefs.remove(KEY_CAS_PASSWORD_IV)
                prefs[KEY_CAS_PLAIN_FALLBACK] = password
            }
            prefs[KEY_CAS_NAME] = profile.name
            prefs[KEY_CAS_PHONE] = profile.phone
        }
    }

    suspend fun clearCredentials() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_CAS_USERNAME)
            prefs.remove(KEY_CAS_ENC_PASSWORD)
            prefs.remove(KEY_CAS_PASSWORD_IV)
            prefs.remove(KEY_CAS_PLAIN_FALLBACK)
            prefs.remove(KEY_CAS_NAME)
            prefs.remove(KEY_CAS_PHONE)
        }
    }

    // ==================== 考试倒计时 ====================

    val examsFlow: Flow<List<CumtExamItem>> = dataStore.data.map { prefs ->
        val raw = prefs[KEY_EXAMS_JSON].orEmpty()
        if (raw.isBlank()) emptyList()
        else runCatching {
            json.decodeFromString(ListSerializer(CumtExamItem.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    suspend fun saveExams(exams: List<CumtExamItem>) {
        val sorted = exams.sortedWith(compareBy({ it.year }, { it.month }, { it.day }, { it.courseName }))
        dataStore.edit { prefs ->
            prefs[KEY_EXAMS_JSON] = json.encodeToString(ListSerializer(CumtExamItem.serializer()), sorted)
        }
    }

    suspend fun addOrMergeExams(newItems: List<CumtExamItem>, keepCustom: Boolean = true) {
        val current = examsFlow.first()
        val existingSyncedIds = current.filter { it.syncedToSchedule }.mapTo(HashSet()) { it.id }
        val normalizedNew = newItems.map { item ->
            if (item.id in existingSyncedIds) item.copy(syncedToSchedule = true) else item
        }
        val custom = if (keepCustom) current.filter { it.isCustom } else emptyList()
        val merged = (normalizedNew + custom).distinctBy { it.id }
        saveExams(merged)
    }

    suspend fun addCustomExam(item: CumtExamItem) {
        val current = examsFlow.first().toMutableList()
        current.removeAll { it.id == item.id }
        current.add(item)
        saveExams(current)
    }

    suspend fun updateExamSyncFlag(examId: String, synced: Boolean) {
        val current = examsFlow.first().map { item ->
            if (item.id == examId) item.copy(syncedToSchedule = synced) else item
        }
        saveExams(current)
    }

    suspend fun updateAllExamsSyncFlags(syncedIds: Set<String>) {
        val current = examsFlow.first().map { item ->
            if (item.id in syncedIds) item.copy(syncedToSchedule = true) else item
        }
        saveExams(current)
    }

    suspend fun clearAllExamSyncFlags() {
        val current = examsFlow.first().map { item ->
            if (item.syncedToSchedule) item.copy(syncedToSchedule = false) else item
        }
        saveExams(current)
    }

    suspend fun deleteExam(id: String) {
        val current = examsFlow.first().filterNot { it.id == id }
        saveExams(current)
    }

    suspend fun clearAllExams() {
        saveExams(emptyList())
    }

    // ==================== 成绩与绩点计算 ====================

    val scoresFlow: Flow<List<CumtScoreItem>> = dataStore.data.map { prefs ->
        val raw = prefs[KEY_SCORES_JSON].orEmpty()
        if (raw.isBlank()) emptyList()
        else runCatching {
            json.decodeFromString(ListSerializer(CumtScoreItem.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    val scoreMappingsFlow: Flow<List<CumtScoreMappingEntry>> = dataStore.data.map { prefs ->
        val raw = prefs[KEY_SCORE_MAPPINGS_JSON].orEmpty()
        if (raw.isBlank()) CumtDefaultScoreMappings.entries
        else runCatching {
            json.decodeFromString(ListSerializer(CumtScoreMappingEntry.serializer()), raw)
        }.getOrDefault(CumtDefaultScoreMappings.entries)
    }

    val scoreRateFlow: Flow<Double> = dataStore.data.map { prefs ->
        prefs[KEY_SCORE_RATE] ?: 1.0
    }

    suspend fun saveScores(scores: List<CumtScoreItem>) {
        dataStore.edit { prefs ->
            prefs[KEY_SCORES_JSON] = json.encodeToString(ListSerializer(CumtScoreItem.serializer()), scores)
        }
    }

    suspend fun saveScoreMappings(mappings: List<CumtScoreMappingEntry>) {
        dataStore.edit { prefs ->
            prefs[KEY_SCORE_MAPPINGS_JSON] =
                json.encodeToString(ListSerializer(CumtScoreMappingEntry.serializer()), mappings)
        }
    }

    suspend fun resetScoreMappings() {
        saveScoreMappings(CumtDefaultScoreMappings.entries)
    }

    suspend fun saveScoreRate(rate: Double) {
        dataStore.edit { prefs ->
            prefs[KEY_SCORE_RATE] = rate
        }
    }

    // ==================== 校园一卡通 & 宿舍电量缓存 ====================

    val campusCardFlow: Flow<CumtCampusCardInfo> = dataStore.data.map { prefs ->
        val raw = prefs[KEY_CARD_CACHE_JSON].orEmpty()
        if (raw.isBlank()) CumtCampusCardInfo()
        else runCatching {
            json.decodeFromString(CumtCampusCardInfo.serializer(), raw)
        }.getOrDefault(CumtCampusCardInfo())
    }

    suspend fun saveCampusCard(info: CumtCampusCardInfo) {
        dataStore.edit { prefs ->
            prefs[KEY_CARD_CACHE_JSON] = json.encodeToString(CumtCampusCardInfo.serializer(), info)
        }
    }

    val dormPowerFlow: Flow<CumtDormPowerInfo> = dataStore.data.map { prefs ->
        val raw = prefs[KEY_DORM_POWER_JSON].orEmpty()
        if (raw.isBlank()) CumtDormPowerInfo()
        else runCatching {
            json.decodeFromString(CumtDormPowerInfo.serializer(), raw)
        }.getOrDefault(CumtDormPowerInfo())
    }

    suspend fun saveDormPower(info: CumtDormPowerInfo) {
        dataStore.edit { prefs ->
            prefs[KEY_DORM_POWER_JSON] = json.encodeToString(CumtDormPowerInfo.serializer(), info)
        }
    }

    // ==================== 校园网多账号与自动登录配置 ====================

    val campusNetAccountsFlow: Flow<List<CampusNetAccount>> = dataStore.data.map { prefs ->
        val raw = prefs[KEY_NET_ACCOUNTS_JSON].orEmpty()
        if (raw.isBlank()) emptyList()
        else runCatching {
            json.decodeFromString(ListSerializer(CampusNetAccount.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    val campusNetAutoLoginFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_NET_AUTO_LOGIN] ?: false
    }

    val lastCampusNetConfigFlow: Flow<Pair<String, String>> = dataStore.data.map { prefs ->
        (prefs[KEY_NET_LAST_CAMPUS] ?: CampusNetCampus.NANHU.code) to
            (prefs[KEY_NET_LAST_ISP] ?: CampusNetIsp.CUMT.id)
    }

    suspend fun setCampusNetAutoLogin(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_NET_AUTO_LOGIN] = enabled }
    }

    suspend fun saveLastCampusNetConfig(campusCode: String, ispId: String) {
        dataStore.edit { prefs ->
            prefs[KEY_NET_LAST_CAMPUS] = campusCode
            prefs[KEY_NET_LAST_ISP] = ispId
        }
    }

    fun decryptCampusNetPassword(account: CampusNetAccount): String {
        if (account.encryptedPassword.isEmpty()) return ""
        if (account.iv.isEmpty()) return account.encryptedPassword
        return secureCrypto.decrypt(account.encryptedPassword, account.iv) ?: account.encryptedPassword
    }

    suspend fun saveCampusNetAccount(
        id: String,
        username: String,
        password: String,
        campusCode: String,
        ispId: String,
        remark: String = ""
    ) {
        val crypto = secureCrypto.encrypt(password)
        val newEntry = CampusNetAccount(
            id = id,
            username = username.trim(),
            encryptedPassword = crypto?.encryptedData ?: password,
            iv = crypto?.iv ?: "",
            campusCode = campusCode,
            ispId = ispId,
            remark = remark.trim()
        )
        val current = campusNetAccountsFlow.first().filterNot {
            it.id == id || (it.username == newEntry.username && it.campusCode == campusCode && it.ispId == ispId)
        }
        val updated = listOf(newEntry) + current
        dataStore.edit { prefs ->
            prefs[KEY_NET_ACCOUNTS_JSON] =
                json.encodeToString(ListSerializer(CampusNetAccount.serializer()), updated)
            prefs[KEY_NET_LAST_CAMPUS] = campusCode
            prefs[KEY_NET_LAST_ISP] = ispId
        }
    }

    suspend fun deleteCampusNetAccount(id: String) {
        val updated = campusNetAccountsFlow.first().filterNot { it.id == id }
        dataStore.edit { prefs ->
            prefs[KEY_NET_ACCOUNTS_JSON] =
                json.encodeToString(ListSerializer(CampusNetAccount.serializer()), updated)
        }
    }

    // ==================== AI 识图 API Key 配置 ====================

    val aiConfigFlow: Flow<CumtAiConfig> = dataStore.data.map { prefs ->
        val preset = CumtAiProviderPreset.fromId(prefs[KEY_AI_PROVIDER_ID])
        val hasKey = !prefs[KEY_AI_ENC_API_KEY].isNullOrEmpty() ||
            !prefs[KEY_AI_PLAIN_FALLBACK].isNullOrEmpty()
        CumtAiConfig(
            providerId = preset.id,
            baseUrl = prefs[KEY_AI_BASE_URL] ?: preset.defaultBaseUrl,
            model = prefs[KEY_AI_MODEL] ?: preset.defaultModel,
            protocol = prefs[KEY_AI_PROTOCOL] ?: preset.protocol.name,
            hasApiKey = hasKey,
            floatingOrbEnabled = prefs[KEY_AI_ORB_ENABLED] ?: true,
            floatingOrbYFraction = (prefs[KEY_AI_ORB_Y_FRACTION] ?: 0.72f).coerceIn(0.12f, 0.85f),
            floatingOrbOnRight = prefs[KEY_AI_ORB_ON_RIGHT] ?: true,
            autoExecuteWrites = prefs[KEY_AI_AUTO_EXEC_WRITES] ?: false
        )
    }

    val cachedAiModelsFlow: Flow<List<CumtAiModelInfo>> = dataStore.data.map { prefs ->
        val raw = prefs[KEY_AI_CACHED_MODELS_JSON].orEmpty()
        if (raw.isBlank()) emptyList()
        else runCatching {
            json.decodeFromString(ListSerializer(CumtAiModelInfo.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    suspend fun saveCachedAiModels(models: List<CumtAiModelInfo>) {
        dataStore.edit { prefs ->
            prefs[KEY_AI_CACHED_MODELS_JSON] =
                json.encodeToString(ListSerializer(CumtAiModelInfo.serializer()), models)
        }
    }

    suspend fun setFloatingOrbEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_AI_ORB_ENABLED] = enabled }
    }

    suspend fun saveFloatingOrbPosition(yFraction: Float, onRight: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_AI_ORB_Y_FRACTION] = yFraction.coerceIn(0.12f, 0.85f)
            prefs[KEY_AI_ORB_ON_RIGHT] = onRight
        }
    }

    suspend fun setAiAutoExecuteWrites(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_AI_AUTO_EXEC_WRITES] = enabled }
    }

    // ==================== MCP Server 配置 ====================

    val mcpConfigFlow: Flow<CumtMcpConfig> = dataStore.data.map { prefs ->
        CumtMcpConfig(
            enabled = prefs[KEY_MCP_ENABLED] ?: false,
            port = (prefs[KEY_MCP_PORT] ?: 8765).coerceIn(1024, 65535),
            token = prefs[KEY_MCP_TOKEN].orEmpty(),
            allowWrite = prefs[KEY_MCP_ALLOW_WRITE] ?: false,
            bindAllInterfaces = prefs[KEY_MCP_BIND_ALL] ?: true
        )
    }

    suspend fun saveMcpConfig(config: CumtMcpConfig) {
        dataStore.edit { prefs ->
            prefs[KEY_MCP_ENABLED] = config.enabled
            prefs[KEY_MCP_PORT] = config.port.coerceIn(1024, 65535)
            prefs[KEY_MCP_TOKEN] = config.token.trim()
            prefs[KEY_MCP_ALLOW_WRITE] = config.allowWrite
            prefs[KEY_MCP_BIND_ALL] = config.bindAllInterfaces
        }
    }

    suspend fun getSavedAiApiKey(): String {
        val prefs = dataStore.data.first()
        val enc = prefs[KEY_AI_ENC_API_KEY].orEmpty()
        val iv = prefs[KEY_AI_API_KEY_IV].orEmpty()
        val decrypted = if (enc.isNotEmpty() && iv.isNotEmpty()) {
            secureCrypto.decrypt(enc, iv)
        } else {
            null
        }
        return (decrypted ?: prefs[KEY_AI_PLAIN_FALLBACK].orEmpty()).trim()
    }

    suspend fun saveAiConfig(
        providerId: String,
        baseUrl: String,
        model: String,
        protocol: CumtAiProtocol,
        apiKey: String
    ) {
        val trimmedKey = apiKey.trim()
        val crypto = if (trimmedKey.isNotEmpty()) secureCrypto.encrypt(trimmedKey) else null
        dataStore.edit { prefs ->
            prefs[KEY_AI_PROVIDER_ID] = providerId
            prefs[KEY_AI_BASE_URL] = baseUrl.trim()
            prefs[KEY_AI_MODEL] = model.trim()
            prefs[KEY_AI_PROTOCOL] = protocol.name
            if (trimmedKey.isEmpty()) {
                prefs.remove(KEY_AI_ENC_API_KEY)
                prefs.remove(KEY_AI_API_KEY_IV)
                prefs.remove(KEY_AI_PLAIN_FALLBACK)
            } else if (crypto != null) {
                prefs[KEY_AI_ENC_API_KEY] = crypto.encryptedData
                prefs[KEY_AI_API_KEY_IV] = crypto.iv
                prefs.remove(KEY_AI_PLAIN_FALLBACK)
            } else {
                prefs.remove(KEY_AI_ENC_API_KEY)
                prefs.remove(KEY_AI_API_KEY_IV)
                prefs[KEY_AI_PLAIN_FALLBACK] = trimmedKey
            }
        }
    }

    suspend fun clearAiApiKey() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_AI_ENC_API_KEY)
            prefs.remove(KEY_AI_API_KEY_IV)
            prefs.remove(KEY_AI_PLAIN_FALLBACK)
        }
    }
}
