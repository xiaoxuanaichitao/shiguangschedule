package com.xingheyuzhuan.shiguangschedule.cumt

import com.xingheyuzhuan.shiguangschedule.ui.components.ToastManager
import kotlinx.coroutines.flow.first
import org.koin.core.annotation.Single

@Single
class CumtAutoLoginCoordinator(
    private val localRepo: CumtLocalRepository,
    private val serviceRepo: CumtServiceRepository,
    private val platformBridge: CumtPlatformBridge
) {
    suspend fun tryAutoLoginOnStartup() {
        val enabled = runCatching { localRepo.campusNetAutoLoginFlow.first() }.getOrDefault(false)
        if (!enabled || !platformBridge.isWifiConnected()) return

        val accounts = runCatching { localRepo.campusNetAccountsFlow.first() }.getOrDefault(emptyList())
        val firstAccount = accounts.firstOrNull() ?: return
        val pwd = localRepo.decryptCampusNetPassword(firstAccount)
        if (firstAccount.username.isBlank() || pwd.isBlank()) return

        val campus = CampusNetCampus.fromCode(firstAccount.campusCode)
        val isp = CampusNetIsp.fromId(firstAccount.ispId)
        val result = serviceRepo.loginCampusNet(firstAccount.username, pwd, campus, isp)
        if (result.success && !result.alreadyLoggedIn) {
            ToastManager.show("校园网已自动认证 (${firstAccount.username})")
        }
    }
}
