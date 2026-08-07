@file:OptIn(ExperimentalQonversionApi::class)

package com.qonversion.android.sdk.dto.remoteconfig

import com.qonversion.android.sdk.ExperimentalQonversionApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * The v2 configuration is rejected at construction rather than at `build()`: it carries values the
 * app cannot invent, so failing at the line that supplies them is what makes the mistake findable.
 */
internal class QRemoteConfigV2ConfigTest {

    @Test
    fun `a well formed configuration is accepted verbatim`() {
        val config = QRemoteConfigV2Config(
            baseUrl = "https://gateway.example.com/",
            environmentUid = "production",
            projectId = 42,
            contextFingerprint = FINGERPRINT,
        )

        assertEquals("https://gateway.example.com/", config.baseUrl)
        assertEquals("production", config.environmentUid)
        assertEquals(42L, config.projectId)
        assertEquals(FINGERPRINT, config.contextFingerprint)
    }

    @Test
    fun `every malformed field is rejected`() {
        val malformed = listOf<Pair<String, () -> QRemoteConfigV2Config>>(
            "relative base url" to { config(baseUrl = "gateway.example.com") },
            "scheme-less base url" to { config(baseUrl = "//gateway.example.com") },
            "empty environment" to { config(environmentUid = "") },
            "over-long environment" to { config(environmentUid = "e".repeat(37)) },
            "zero project id" to { config(projectId = 0) },
            "negative project id" to { config(projectId = -1) },
            "uppercase fingerprint" to { config(contextFingerprint = FINGERPRINT.uppercase()) },
            "short fingerprint" to { config(contextFingerprint = "a".repeat(63)) },
            "non-hex fingerprint" to { config(contextFingerprint = "z".repeat(64)) },
        )

        malformed.forEach { (name, build) ->
            assertThrows(name, IllegalArgumentException::class.java) { build() }
        }
    }

    private fun config(
        baseUrl: String = "https://gateway.example.com/",
        environmentUid: String = "production",
        projectId: Long = 42,
        contextFingerprint: String = FINGERPRINT,
    ) = QRemoteConfigV2Config(baseUrl, environmentUid, projectId, contextFingerprint)

    private companion object {
        const val FINGERPRINT = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
    }
}
