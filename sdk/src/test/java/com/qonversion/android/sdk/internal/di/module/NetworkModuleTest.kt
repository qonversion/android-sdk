package com.qonversion.android.sdk.internal.di.module

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.qonversion.android.sdk.internal.api.NetworkInterceptor
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import javax.net.ssl.SSLSession

@RunWith(RobolectricTestRunner::class)
internal class NetworkModuleTest {
    @Test
    fun `api client rejects a hostname that does not match the certificate`() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val interceptor = mockk<NetworkInterceptor>(relaxed = true)
        val sslSession = mockk<SSLSession>(relaxed = true)

        val client = NetworkModule().provideOkHttpClient(application, interceptor)

        assertFalse(client.hostnameVerifier().verify("attacker.invalid", sslSession))
    }
}
