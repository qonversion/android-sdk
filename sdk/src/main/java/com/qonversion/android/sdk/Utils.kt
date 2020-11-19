package com.qonversion.android.sdk

import android.app.Application
import com.qonversion.android.sdk.billing.milliSecondsToSeconds

class Utils internal constructor(private val context: Application) {
    private var installDate: Long = 0

    fun getInstallDate(): Long {
        if (installDate > 0) {
            return installDate
        }

        installDate = context.packageManager.getPackageInfo(
            context.packageName,
            0
        ).firstInstallTime.milliSecondsToSeconds()

        return installDate
    }
}