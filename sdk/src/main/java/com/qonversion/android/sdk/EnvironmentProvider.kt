package com.qonversion.android.sdk

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager
import com.qonversion.android.sdk.dto.Environment
import com.qonversion.android.sdk.dto.device.Os
import java.util.*

class EnvironmentProvider(private val context: Context) {

    companion object {
        private const val UNKNOWN = "UNKNOWN"
    }

    fun getInfo(idfa: String? = null, pushToken: String? = null): Environment = Environment(
        getVersionName(),
        getCarrier(),
        getDeviceId(),
        getLocale(),
        Build.MANUFACTURER,
        Build.MODEL,
        Os().name,
        Os().version,
        getTimeZone(),
        "android",
        getCountry(),
        idfa,
        pushToken
    )

    private fun getVersionName(): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    } catch (throwable: Throwable) {
        UNKNOWN
    }

    @SuppressLint("HardwareIds")
    private fun getDeviceId(): String =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)

    private fun getCarrier(): String {
        val manager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        return manager.networkOperatorName
    }

    private fun getLocale() = Locale.getDefault().language

    private fun getCountry() = Locale.getDefault().country

    private fun getTimeZone() = TimeZone.getDefault().id
}
