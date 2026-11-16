package com.xingheyuzhuan.shiguangschedule.data.repository

import com.xingheyuzhuan.shiguangschedule.data.model.ContributionList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single
import shiguangschedule.shared.generated.resources.Res


/**
 * 贡献者数据仓库
 */
@Single
class ContributionRepository {

    private val json = Json {
        ignoreUnknownKeys = true
    }

    /**
     * 读取贡献者 JSON 数据并反序列化。
     */
    suspend fun getContributions(): ContributionList = withContext(Dispatchers.IO) {
        try {
            // 直接跨平台读取打包进去的静态资源文件
            val bytes = Res.readBytes("files/contributors_data/contributors.json")
            val jsonString = bytes.decodeToString()
            return@withContext json.decodeFromString<ContributionList>(jsonString)
        } catch (_: Exception) {
            return@withContext ContributionList(appDev = emptyList(), jiaowuadapter = emptyList())
        }
    }
}