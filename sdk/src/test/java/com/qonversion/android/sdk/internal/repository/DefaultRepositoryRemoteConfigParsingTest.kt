package com.qonversion.android.sdk.internal.repository

import com.qonversion.android.sdk.dto.QEnvironment
import com.qonversion.android.sdk.dto.QLaunchMode
import com.qonversion.android.sdk.dto.QRemoteConfigList
import com.qonversion.android.sdk.dto.QonversionError
import com.qonversion.android.sdk.dto.QonversionErrorCode
import com.qonversion.android.sdk.dto.entitlements.QEntitlementsCacheLifetime
import com.qonversion.android.sdk.internal.EnvironmentProvider
import com.qonversion.android.sdk.internal.IncrementalDelayCalculator
import com.qonversion.android.sdk.internal.InternalConfig
import com.qonversion.android.sdk.internal.api.Api
import com.qonversion.android.sdk.internal.api.ApiErrorMapper
import com.qonversion.android.sdk.internal.api.ApiHelper
import com.qonversion.android.sdk.internal.di.module.NetworkModule
import com.qonversion.android.sdk.internal.dto.config.CacheConfig
import com.qonversion.android.sdk.internal.dto.config.PrimaryConfig
import com.qonversion.android.sdk.internal.logger.Logger
import com.qonversion.android.sdk.listeners.QonversionRemoteConfigCallback
import com.qonversion.android.sdk.listeners.QonversionRemoteConfigListCallback
import io.mockk.mockk
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.Random
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

internal class DefaultRepositoryRemoteConfigParsingTest {
    @Test
    fun `real Moshi JsonDataException maps to response parsing failed`() {
        val fixture = repositoryRespondingWith(
            """{"payload":"not-an-object","experiment":null,"source":null}""",
        )
        try {
            val receivedError = AtomicReference<QonversionError>()
            val success = AtomicReference<Any>()
            val completed = CountDownLatch(1)

            fixture.repository.remoteConfig("ctx", object : QonversionRemoteConfigCallback {
                override fun onSuccess(remoteConfig: com.qonversion.android.sdk.dto.QRemoteConfig) {
                    success.set(remoteConfig)
                    completed.countDown()
                }

                override fun onError(error: QonversionError) {
                    receivedError.set(error)
                    completed.countDown()
                }
            })

            assertTrue(completed.await(5, TimeUnit.SECONDS))
            assertNull(success.get())
            assertEquals(QonversionErrorCode.ResponseParsingFailed, receivedError.get()?.code)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `list containing any semantically invalid config fails as one response`() {
        val fixture = repositoryRespondingWith(
            """[
                {
                  "payload":{"value":"valid"},
                  "experiment":null,
                  "source":{
                    "uid":"rc-valid",
                    "name":"Valid",
                    "assignment_type":"auto",
                    "type":"remote_configuration",
                    "context_key":"valid"
                  }
                },
                {"payload":{"value":"invalid"},"experiment":null,"source":null}
              ]""".trimIndent(),
        )
        try {
            val loads = listOf<(QonversionRemoteConfigListCallback) -> Unit>(
                fixture.repository::remoteConfigList,
                { callback -> fixture.repository.remoteConfigList(listOf("valid"), false, callback) },
            )
            loads.forEach { load ->
                val receivedError = AtomicReference<QonversionError>()
                val success = AtomicReference<QRemoteConfigList>()
                val completed = CountDownLatch(1)
                val callback = object : QonversionRemoteConfigListCallback {
                    override fun onSuccess(remoteConfigList: QRemoteConfigList) {
                        success.set(remoteConfigList)
                        completed.countDown()
                    }

                    override fun onError(error: QonversionError) {
                        receivedError.set(error)
                        completed.countDown()
                    }
                }

                load(callback)

                assertTrue(completed.await(5, TimeUnit.SECONDS))
                assertNull(success.get())
                assertEquals(QonversionErrorCode.ResponseParsingFailed, receivedError.get()?.code)
            }
        } finally {
            fixture.close()
        }
    }

    private fun repositoryRespondingWith(json: String): RepositoryFixture {
        val mediaType = MediaType.parse("application/json")
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(ResponseBody.create(mediaType, json))
                    .build()
            }
            .build()
        val moshi = NetworkModule().provideMoshi()
        val api = Retrofit.Builder()
            .baseUrl("https://example.test/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(Api::class.java)
        val config = InternalConfig(
            PrimaryConfig("project", QLaunchMode.SubscriptionManagement, QEnvironment.Production),
            CacheConfig(QEntitlementsCacheLifetime.Month, null),
        ).also { it.uid = "user" }
        val repository = DefaultRepository(
            api = api,
            environmentProvider = mockk<EnvironmentProvider>(relaxed = true),
            config = config,
            logger = mockk<Logger>(relaxed = true),
            errorMapper = ApiErrorMapper(ApiHelper(config.apiUrl)),
            delayCalculator = IncrementalDelayCalculator(Random(0)),
        )
        return RepositoryFixture(repository, client)
    }

    private data class RepositoryFixture(
        val repository: DefaultRepository,
        val client: OkHttpClient,
    ) {
        fun close() {
            client.dispatcher().executorService().shutdownNow()
            client.connectionPool().evictAll()
        }
    }
}
