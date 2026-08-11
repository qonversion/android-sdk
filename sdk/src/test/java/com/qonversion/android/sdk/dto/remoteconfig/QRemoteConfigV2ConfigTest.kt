@file:OptIn(ExperimentalQonversionApi::class)

package com.qonversion.android.sdk.dto.remoteconfig

import com.qonversion.android.sdk.ExperimentalQonversionApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        )

        assertEquals("https://gateway.example.com/", config.baseUrl)
        assertEquals("production", config.environmentUid)
        // Unset interval means "auto": the build-mode-dependent default is resolved later, so the
        // configuration itself carries the sentinel untouched.
        assertEquals(0, config.minFetchIntervalSeconds)
    }

    @Test
    fun `an explicit minimum fetch interval is accepted verbatim`() {
        assertEquals(300, config(minFetchIntervalSeconds = 300).minFetchIntervalSeconds)
        assertEquals(0, config(minFetchIntervalSeconds = 0).minFetchIntervalSeconds)
    }

    @Test
    fun `every malformed field is rejected`() {
        val malformed = listOf<Pair<String, () -> QRemoteConfigV2Config>>(
            "relative base url" to { config(baseUrl = "gateway.example.com") },
            "scheme-less base url" to { config(baseUrl = "//gateway.example.com") },
            "empty environment" to { config(environmentUid = "") },
            "over-long environment" to { config(environmentUid = "e".repeat(37)) },
            "negative fetch interval" to { config(minFetchIntervalSeconds = -1) },
        )

        malformed.forEach { (name, build) ->
            assertThrows(name, IllegalArgumentException::class.java) { build() }
        }
    }

    @Test
    fun `the configuration neither takes nor exposes a project id`() {
        // The numeric project id is learned from the gateway session bootstrap. Re-introducing it
        // here would put a value the app cannot verify back into the public surface. Asserted by
        // name rather than by shape, so an unrelated field of the same type does not fail this.
        val members = QRemoteConfigV2Config::class.java.declaredFields.map { it.name } +
            QRemoteConfigV2Config::class.java.declaredMethods.map { it.name }
        members.forEach { name -> assertFalse(name, name.contains("rojectId")) }
        assertEquals(
            setOf("baseUrl", "environmentUid", "minFetchIntervalSeconds"),
            QRemoteConfigV2Config::class.java.declaredFields.map { it.name }.toSet(),
        )
    }

    private fun config(
        baseUrl: String = "https://gateway.example.com/",
        environmentUid: String = "production",
        minFetchIntervalSeconds: Long = 0,
    ) = QRemoteConfigV2Config(baseUrl, environmentUid, minFetchIntervalSeconds)
}
