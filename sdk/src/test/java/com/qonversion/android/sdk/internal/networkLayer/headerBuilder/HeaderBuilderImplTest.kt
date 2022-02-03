package com.qonversion.android.sdk.internal.networkLayer.headerBuilder

import android.os.Build
import com.qonversion.android.sdk.internal.common.DEBUG_MODE_PREFIX
import com.qonversion.android.sdk.internal.common.PLATFORM
import com.qonversion.android.sdk.internal.common.StorageConstants
import com.qonversion.android.sdk.internal.common.localStorage.LocalStorage
import com.qonversion.android.sdk.internal.networkLayer.ApiHeader
import com.qonversion.android.sdk.internal.provider.EnvironmentProvider
import com.qonversion.android.sdk.internal.provider.PrimaryConfigProvider
import com.qonversion.android.sdk.internal.provider.UidProvider
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Locale

internal class HeaderBuilderTest {

    private lateinit var headerBuilder: HeaderBuilderImpl
    private val localStorage = mockk<LocalStorage>()
    private val locale = mockk<Locale>()
    private val primaryConfigProvider = mockk<PrimaryConfigProvider>()
    private val environmentProvider = mockk<EnvironmentProvider>()
    private val uidProvider = mockk<UidProvider>()

    private val testSourceKey = "test source key"
    private val testVersionKey = "test version key"
    private val testLanguage = "test language"

    private val testSdkVersion = "test sdk version"
    private val testProjectKey = "test project key"
    private val testUid = "test uid"

    private val defaultExpectedHeaders = mapOf(
        ApiHeader.Authorization.key to "Bearer $testProjectKey",
        ApiHeader.Locale.key to testLanguage,
        ApiHeader.Source.key to testSourceKey,
        ApiHeader.SourceVersion.key to testVersionKey,
        ApiHeader.Platform.key to PLATFORM,
        ApiHeader.PlatformVersion.key to Build.VERSION.RELEASE,
        ApiHeader.UserID.key to testUid
    )

    @BeforeEach
    fun setUp() {
        headerBuilder = HeaderBuilderImpl(
            localStorage,
            locale,
            primaryConfigProvider,
            environmentProvider,
            uidProvider
        )

        every {
            localStorage.getString(StorageConstants.SourceKey.key, any())
        } returns testSourceKey
        every {
            localStorage.getString(StorageConstants.VersionKey.key, any())
        } returns testVersionKey
        every { locale.language } returns testLanguage
        every { environmentProvider.isSandbox } returns false
        every { uidProvider.uid } returns testUid
        every { primaryConfigProvider.primaryConfig.sdkVersion } returns testSdkVersion
        every { primaryConfigProvider.primaryConfig.projectKey } returns testProjectKey
    }

    @Test
    fun `common headers test`() {
        // given

        // when
        val headers = headerBuilder.buildCommonHeaders()

        // then
        assertThat(headers).containsExactlyEntriesOf(defaultExpectedHeaders)
    }

    @Test
    fun `no local stored data`() {
        // given
        every {
            localStorage.getString(StorageConstants.SourceKey.key, any())
        } returns null
        every {
            localStorage.getString(StorageConstants.VersionKey.key, any())
        } returns null

        val expectedResult = defaultExpectedHeaders +
                (ApiHeader.Source.key to PLATFORM) +
                (ApiHeader.SourceVersion.key to testSdkVersion)

        // when
        val headers = headerBuilder.buildCommonHeaders()

        // then
        assertThat(headers).containsExactlyEntriesOf(expectedResult)
    }

    @Test
    fun `debug mode`() {
        // given
        every {
            environmentProvider.isSandbox
        } returns true

        val expectedResult = defaultExpectedHeaders +
                (ApiHeader.Authorization.key to "Bearer $DEBUG_MODE_PREFIX$testProjectKey")

        // when
        val headers = headerBuilder.buildCommonHeaders()

        // then
        assertThat(headers).containsExactlyEntriesOf(expectedResult)
    }
}
