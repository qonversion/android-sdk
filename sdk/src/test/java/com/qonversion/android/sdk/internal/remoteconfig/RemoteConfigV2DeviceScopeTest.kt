@file:OptIn(ExperimentalQonversionApi::class)

package com.qonversion.android.sdk.internal.remoteconfig

import com.qonversion.android.sdk.ExperimentalQonversionApi
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import java.util.concurrent.TimeUnit

private const val FIRST_INSTALL_TIME_MILLIS = 1_577_836_800_123L
private const val EXPECTED_INSTALLED_AT_SECONDS = 1_577_836_800L
private const val POLL_INTERVAL_MILLIS = 10L

/**
 * Proves the device-scoped part of the client context end to end, through the public fetch path.
 *
 * The provider under test is the real one, reading a real (Robolectric) `PackageManager`: an
 * identity switch must not move `device_installed_at`, because the server evaluates account age as
 * `min(device_installed_at, client.created_at)` and a post-logout client row is always brand new.
 */
@RunWith(RobolectricTestRunner::class)
internal class RemoteConfigV2DeviceScopeTest {
    private var harness: RemoteConfigV2Harness? = null

    @After
    fun tearDown() {
        harness?.shutdown()
    }

    @Test
    fun `device_installed_at is identical across an identity switch`() {
        val application = RuntimeEnvironment.getApplication()
        shadowOf(application.packageManager)
            .getInternalMutablePackageInfo(application.packageName)
            .apply {
                firstInstallTime = FIRST_INSTALL_TIME_MILLIS
                versionName = "1.2.3"
            }
        val started = RemoteConfigV2Harness(
            clientContextProvider = DeviceRemoteConfigClientContextProvider(application, "9.7.0"),
        ).also { harness = it }

        started.identify("QON_anon_a", "canonical-a", RemoteConfigFetchForceReason.Build)
        started.fetchBlocking()
        started.identify("QON_anon_b", "canonical-b", RemoteConfigFetchForceReason.Logout)
        awaitSnapshotReads(started, count = 2)

        val installedAt = started.snapshotRequests.map { body ->
            Regex("\"device_installed_at\":(\\d+)").find(body)?.groupValues?.get(1)
        }
        assertTrue("expected a snapshot read per identity", installedAt.size >= 2)
        // Without this the test would also pass if the identity switch had silently no-op'd.
        assertTrue(started.sessionRequests.any { it.contains("QON_anon_a") })
        assertTrue(started.sessionRequests.any { it.contains("QON_anon_b") })
        assertEquals(setOf(EXPECTED_INSTALLED_AT_SECONDS.toString()), installedAt.toSet())
    }

    private fun awaitSnapshotReads(harness: RemoteConfigV2Harness, count: Int) {
        val deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(RC_AWAIT_SECONDS)
        while (harness.snapshotRequests.size < count && System.currentTimeMillis() < deadline) {
            Thread.sleep(POLL_INTERVAL_MILLIS)
        }
    }
}
