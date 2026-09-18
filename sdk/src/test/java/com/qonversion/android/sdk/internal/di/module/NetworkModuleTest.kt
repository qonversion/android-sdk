package com.qonversion.android.sdk.internal.di.module

import android.app.Application
import com.qonversion.android.sdk.internal.api.NetworkInterceptor
import io.mockk.every
import io.mockk.mockk
import okhttp3.OkHttpClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.nio.file.Files

internal class NetworkModuleTest {

    // Guards DEV-1837: sdk 9.3.0-9.7.0 shipped an OkHttpClient with a trust-all X509TrustManager
    // and a hostname verifier that accepted every host. The default verifier is a singleton,
    // so identity proves no custom verifier is installed.
    @Test
    fun `provided OkHttpClient keeps the default hostname verifier`() {
        val context = mockk<Application>(relaxed = true)
        every { context.cacheDir } returns Files.createTempDirectory("qonversion-test-cache").toFile()
        val interceptor = mockk<NetworkInterceptor>(relaxed = true)

        val client = NetworkModule().provideOkHttpClient(context, interceptor)

        assertThat(client.hostnameVerifier()).isSameAs(OkHttpClient.Builder().build().hostnameVerifier())
    }
}
