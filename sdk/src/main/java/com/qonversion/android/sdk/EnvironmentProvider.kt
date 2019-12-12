package com.qonversion.android.sdk

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.DisplayMetrics
import android.view.WindowManager
import com.qonversion.android.sdk.dto.Environment
import com.qonversion.android.sdk.dto.app.App
import com.qonversion.android.sdk.dto.device.AdsDto
import com.qonversion.android.sdk.dto.device.Device
import com.qonversion.android.sdk.dto.device.Os
import com.qonversion.android.sdk.dto.device.Screen
import java.util.*

internal class EnvironmentProvider(private val context: Context) {

    companion object {
        private const val UNKNOWN = "UNKNOWN"
    }

    fun getInfo(ads: AdsDto): Environment = Environment(getAppInfo(), getDeviceInfo(ads))

    private fun getAppInfo(): App = App(
        name = getAppName(),
        version = getVersionName(),
        build = getBuildName(),
        bundle = getAppId()
    )

    private fun getDeviceInfo(ads: AdsDto): Device = Device(
        os = Os(),
        screen = getScreenResolution(),
        deviceId = getDeviceId(),
        model = Build.MODEL,
        carrier = getCarrier(),
        locale = getLocale(),
        timezone = getTimeZone(),
        ads = ads
    )

    private fun getAppId(): String = context.packageName

    private fun getVersionName(): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    } catch (throwable: Throwable) {
        UNKNOWN
    }

    private fun getBuildName(): String = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            context.packageManager
                .getPackageInfo(context.packageName, 0).longVersionCode.toString()
        } else {
            context.packageManager
                .getPackageInfo(context.packageName, 0).versionCode.toString()
        }
    } catch (throwable: Throwable) {
        UNKNOWN
    }

    private fun getAppName(): String = try {
        val applicationInfo = context.applicationInfo
        val stringId = applicationInfo.labelRes
        if (stringId == 0) {
            applicationInfo.nonLocalizedLabel.toString()
        } else {
            context.getString(stringId)
        }
    } catch (throwable: Throwable) {
        UNKNOWN
    }

    private fun getScreenResolution(): Screen {
        val metrics = DisplayMetrics().apply {
            (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.getMetrics(this)
        }
        val width = metrics.widthPixels
        val height = metrics.heightPixels

        return Screen(width.toString(), height.toString())
    }

    @SuppressLint("HardwareIds")
    private fun getDeviceId(): String =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)


    private fun getCarrier(): String {
        val manager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        return manager.networkOperatorName
    }

    private fun getLocale() = Locale.getDefault().language

    private fun getTimeZone() = TimeZone.getDefault().getDisplayName(false, TimeZone.SHORT)
}