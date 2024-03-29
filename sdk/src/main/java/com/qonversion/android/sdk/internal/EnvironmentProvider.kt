package com.qonversion.android.sdk.internal

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager
import com.qonversion.android.sdk.internal.dto.Environment
import com.qonversion.android.sdk.internal.dto.device.Os
import java.util.*

internal class EnvironmentProvider(
    private val context: Context
) {
    companion object {
        private const val UNKNOWN = "UNKNOWN"
    }

    fun getInfo(idfa: String? = null): Environment = Environment(
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
        idfa
    )

    fun getVersionName(): String {
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0L)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }

            packageInfo.versionName
        } catch (throwable: Throwable) {
            UNKNOWN
        }
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
