package com.xingheyuzhuan.shiguangschedule.cumt

import org.koin.core.annotation.Single

@Single
class IosCumtPlatformBridge : CumtPlatformBridge {
    override fun isWifiConnected(): Boolean = false
}
