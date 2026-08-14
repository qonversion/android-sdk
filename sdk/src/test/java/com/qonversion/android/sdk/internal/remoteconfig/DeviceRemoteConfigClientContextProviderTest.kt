package com.qonversion.android.sdk.internal.remoteconfig

import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/**
 * Pins the DEVICE scope of `device_installed_at`.
 *
 * Robolectric gives a real [android.content.pm.PackageManager] whose `firstInstallTime` can be
 * controlled, which is the only way to prove the provider reads the device install date rather
 * than anything identity-shaped.
 */
@RunWith(RobolectricTestRunner::class)
internal class DeviceRemoteConfigClientContextProviderTest {

    @Test
    fun `device_installed_at is the package first install time in epoch seconds`() {
        setFirstInstallTime(1_577_836_800_123)

        val context = requireNotNull(provider().clientContext())

        assertEquals(1_577_836_800, context.deviceInstalledAtSeconds)
        assertEquals("android", context.platform)
        assertEquals("9.7.0", context.sdkVersion)
        assertEquals(Build.MODEL, context.deviceModel)
        assertNotNull(context.locale)
    }

    @Test
    fun `device_installed_at does not move when the identity does`() {
        // The provider is constructed without any identity input, so a logout / identify cycle
        // cannot reach it. Two independent instances must agree, and must keep agreeing after the
        // SDK would have minted a new anonymous uid.
        setFirstInstallTime(1_577_836_800_000)

        val before = requireNotNull(provider().clientContext()).deviceInstalledAtSeconds
        val after = requireNotNull(provider().clientContext()).deviceInstalledAtSeconds

        assertEquals(1_577_836_800, before)
        assertEquals(before, after)
    }

    private fun provider() = DeviceRemoteConfigClientContextProvider(
        context = RuntimeEnvironment.getApplication(),
        sdkVersion = "9.7.0",
    )

    private fun setFirstInstallTime(millis: Long) {
        val application = RuntimeEnvironment.getApplication()
        val packageInfo = shadowOf(application.packageManager)
            .getInternalMutablePackageInfo(application.packageName)
        packageInfo.firstInstallTime = millis
        packageInfo.versionName = "1.2.3"
    }
}
