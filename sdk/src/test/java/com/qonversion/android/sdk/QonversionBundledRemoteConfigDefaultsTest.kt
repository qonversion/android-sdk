package com.qonversion.android.sdk

import android.content.Context
import com.qonversion.android.sdk.internal.services.BundledRemoteConfigDefaults
import com.qonversion.android.sdk.internal.services.BundledRemoteConfigDefaultsAssetSource
import com.qonversion.android.sdk.internal.services.BundledRemoteConfigDefaultsReader
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream

internal class QonversionBundledRemoteConfigDefaultsTest {
    @After
    fun resetProcessCache() {
        BundledRemoteConfigDefaults.resetForTests()
    }

    @Test
    fun `static fallback getter works without initializing Qonversion`() {
        val context = mockk<Context>(relaxed = true)
        BundledRemoteConfigDefaults.installReaderForTests(
            BundledRemoteConfigDefaultsReader(
                BundledRemoteConfigDefaultsAssetSource {
                    ByteArrayInputStream(SERVER_GOLDEN_ARTIFACT.toByteArray())
                },
            ),
        )

        val value = Qonversion.fallbackRemoteConfigValue(context, "alpha")

        assertNotNull(value)
        assertEquals(mapOf("message" to "Привет 👋"), value?.rawValue)
        assertNull(Qonversion.fallbackRemoteConfigValue(context, "missing"))
    }

    @Test
    fun `Java static API shape accepts only Context and context key`() {
        val method = Qonversion::class.java.getMethod(
            "fallbackRemoteConfigValue",
            Context::class.java,
            String::class.java,
        )

        assertEquals("com.qonversion.android.sdk.dto.QRemoteConfigFallbackValue", method.returnType.name)
    }

    private companion object {
        const val SERVER_GOLDEN_ARTIFACT =
            "{\"schemaVersion\":1,\"projectId\":42,\"environmentUid\":\"env-production\"," +
                "\"releaseUid\":\"release-portable\",\"releaseNumber\":7," +
                "\"manifestContentHash\":\"0291766e896e3f36aca5385082d74e8bd70fabf12ee776b7dfa2cd4d961c9dea\"," +
                "\"defaultsDigest\":\"9e4dfcb4069901c3af4341383c814b24cdc39b20491f7a4593e0036d01f39540\"," +
                "\"defaults\":[{\"key\":\"alpha\",\"variationUid\":\"variation-alpha\"," +
                "\"valueBase64\":\"IHsgIm1lc3NhZ2UiOiAi0J/RgNC40LLQtdGCIPCfkYsiIH0gCg==\"}," +
                "{\"key\":\"beta\",\"variationUid\":\"variation-beta\",\"valueBase64\":\"bnVsbA==\"}]}"
    }
}
