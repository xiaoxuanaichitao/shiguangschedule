package com.xingheyuzhuan.shiguangschedule.cumt

import org.koin.core.annotation.Single

@Single
class JvmCumtPlatformBridge : CumtPlatformBridge {
    override fun isWifiConnected(): Boolean = false
}
