package com.xingheyuzhuan.shiguangschedule.cumt

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.core.annotation.Single
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
@Single
class ShiguangMcpServerManager(
    private val localRepo: CumtLocalRepository,
    val dispatcher: ShiguangMcpDispatcher
) {
    private val platformServer = McpPlatformServer()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val isSupported: Boolean
        get() = platformServer.isSupported

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _activeEndpoint = MutableStateFlow("")
    val activeEndpoint: StateFlow<String> = _activeEndpoint.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val _lanAddresses = MutableStateFlow<List<String>>(listOf("127.0.0.1"))
    val lanAddresses: StateFlow<List<String>> = _lanAddresses.asStateFlow()

    init {
        refreshLanAddresses()
    }

    fun refreshLanAddresses() {
        _lanAddresses.value = platformServer.getLocalLanAddresses()
    }

    suspend fun ensureAutoStartIfEnabled() {
        if (!platformServer.isSupported) return
        val cfg = localRepo.mcpConfigFlow.first()
        if (cfg.enabled && !_isRunning.value) {
            startWithConfig(cfg, persistEnabled = false)
        }
    }

    fun startOrRestart(
        port: Int,
        allowWrite: Boolean,
        bindAllInterfaces: Boolean,
        tokenOverride: String? = null
    ) {
        scope.launch {
            val existing = localRepo.mcpConfigFlow.first()
            val resolvedToken = (tokenOverride ?: existing.token).trim().ifBlank { generateToken() }
            val updated = CumtMcpConfig(
                enabled = true,
                port = port.coerceIn(1024, 65535),
                token = resolvedToken,
                allowWrite = allowWrite,
                bindAllInterfaces = bindAllInterfaces
            )
            startWithConfig(updated, persistEnabled = true)
        }
    }

    private suspend fun startWithConfig(cfg: CumtMcpConfig, persistEnabled: Boolean) {
        _lastError.value = null
        refreshLanAddresses()
        val token = cfg.token.ifBlank { generateToken() }
        val finalCfg = cfg.copy(token = token)
        if (persistEnabled) {
            localRepo.saveMcpConfig(finalCfg)
        }
        val result = platformServer.start(
            port = finalCfg.port,
            bindAllInterfaces = finalCfg.bindAllInterfaces,
            authToken = finalCfg.token,
            onJsonRpc = { rawBody ->
                val latestAllowWrite = localRepo.mcpConfigFlow.first().allowWrite
                dispatcher.handleJsonRpc(rawBody, allowWrite = latestAllowWrite)
            }
        )
        result.fold(
            onSuccess = { endpoint ->
                _isRunning.value = true
                _activeEndpoint.value = endpoint
                _lastError.value = null
            },
            onFailure = { err ->
                _isRunning.value = false
                _activeEndpoint.value = ""
                _lastError.value = err.message ?: "启动 MCP 服务失败"
                if (persistEnabled) {
                    localRepo.saveMcpConfig(finalCfg.copy(enabled = false))
                }
            }
        )
    }

    fun stopServer() {
        scope.launch {
            platformServer.stop()
            _isRunning.value = false
            _activeEndpoint.value = ""
            _lastError.value = null
            val existing = localRepo.mcpConfigFlow.first()
            localRepo.saveMcpConfig(existing.copy(enabled = false))
        }
    }

    fun updateAllowWrite(allowWrite: Boolean) {
        scope.launch {
            val existing = localRepo.mcpConfigFlow.first()
            localRepo.saveMcpConfig(existing.copy(allowWrite = allowWrite))
        }
    }

    fun regenerateToken() {
        scope.launch {
            val existing = localRepo.mcpConfigFlow.first()
            val newToken = generateToken()
            val updated = existing.copy(token = newToken)
            localRepo.saveMcpConfig(updated)
            if (_isRunning.value) {
                startWithConfig(updated, persistEnabled = false)
            }
        }
    }

    private fun generateToken(): String =
        "sg_" + Uuid.random().toString().replace("-", "").take(24)
}
