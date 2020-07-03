package com.qonversion.android.sdk

import com.qonversion.android.sdk.dto.Environment
import com.qonversion.android.sdk.dto.device.AdsDto

interface EnvironmentProvider {
    fun getInfo(internalUserId: String, ads: AdsDto): Environment
}