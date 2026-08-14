package com.qonversion.android.sdk.internal.remoteconfig

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.util.Locale

private const val ANDROID_PLATFORM = "android"
private const val UNKNOWN = "UNKNOWN"
private const val UNDETERMINED_LANGUAGE_TAG = "und"
private const val MILLIS_IN_SECOND = 1_000L

/**
 * Builds the snapshot request's `client_context` from device facts only.
 *
 * The constructor takes no identity on purpose. `device_installed_at` is read from
 * `PackageManager.firstInstallTime`, which is a property of the installed package on this device:
 * it is untouched by `identify()`, by logout, and by the anonymous uid being re-minted. That is
 * exactly the invariant the server relies on — it evaluates account age as
 * `min(device_installed_at, client.created_at)`, so a post-logout client row looks brand new and
 * only the preserved device install date keeps a long-standing user out of "new users" targeting.
 *
 * Reusing the SDK's existing install-date source (`QProductCenterManager` reads the same
 * `firstInstallTime` for `install_date`) keeps a single notion of "when this device installed the
 * app" across the wire.
 */
internal class DeviceRemoteConfigClientContextProvider(
    private val context: Context,
    private val sdkVersion: String,
) : RemoteConfigClientContextProvider {

    override fun clientContext(): RemoteConfigClientContext? {
        val packageInfo = packageInfo() ?: return null
        return RemoteConfigClientContext(
            platform = ANDROID_PLATFORM,
            appVersion = packageInfo.versionName ?: UNKNOWN,
            osVersion = Build.VERSION.RELEASE ?: UNKNOWN,
            sdkVersion = sdkVersion,
            locale = locale(),
            deviceModel = Build.MODEL ?: UNKNOWN,
            deviceInstalledAtSeconds = packageInfo.firstInstallTime
                .coerceAtLeast(0) / MILLIS_IN_SECOND,
        ).takeIf { it.isValid() }
    }

    private fun packageInfo() = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(0L),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
    } catch (_: Exception) {
        null
    }

    /**
     * `Locale.getLanguage()` still returns the pre-1989 ISO-639 codes (`iw`, `in`, `ji` instead of
     * `he`, `id`, `yi`), which would silently miss those users in locale targeting.
     * `toLanguageTag()` gives the modern BCP-47 subtags; the separator is normalised to `_` to
     * match the shape the gateway contract documents (`en_US`).
     */
    private fun locale(): String {
        val tag = try {
            Locale.getDefault().toLanguageTag()
        } catch (_: Exception) {
            ""
        }
        return if (tag.isEmpty() || tag == UNDETERMINED_LANGUAGE_TAG) UNKNOWN else tag.replace('-', '_')
    }
}
