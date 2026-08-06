package com.qonversion.android.sdk.internal.remoteconfig

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.security.MessageDigest

internal class RemoteConfigSnapshotEnvelopeParserTest {
    private val parser = RemoteConfigSnapshotEnvelopeParser()
    private val expectation = RemoteConfigSnapshotEnvelopeExpectation(
        projectId = 42,
        environmentUid = "env-production",
        contextFingerprint = "a".repeat(64),
    )

    @Test
    fun `cross-language canonical body and strong ETag are admitted byte exactly`() {
        val body = requireNotNull(javaClass.getResourceAsStream("/remoteconfigv2/resolved-snapshot-v1.json"))
            .bufferedReader(Charsets.UTF_8)
            .use { it.readLine().encodeToByteArray() }

        val envelope = parser.parse(body, GOLDEN_ETAG, expectation)

        assertNotNull(envelope)
        envelope ?: return
        assertEquals(42, envelope.projectId)
        assertEquals("env-production", envelope.environmentUid)
        assertEquals("a".repeat(64), envelope.contextFingerprint)
        assertEquals("release-uid", envelope.release.releaseUid)
        assertEquals(7, envelope.release.releaseNumber)
        assertEquals(GOLDEN_ETAG, envelope.etag)
        assertEquals(GOLDEN_ETAG.removeSurrounding("\""), envelope.bodyDigest)
        assertArrayEquals(body, envelope.canonicalBodyBytes)
        assertArrayEquals("\"value\"".encodeToByteArray(), envelope.release.entry("alpha")?.rawValueBytes)
        assertArrayEquals("null".encodeToByteArray(), envelope.release.entry("alpha")?.metadataBytes)
        assertArrayEquals("{\"nested\":true}".encodeToByteArray(), envelope.release.entry("zeta")?.rawValueBytes)
        assertArrayEquals(
            "{\"resetNavigation\":true}".encodeToByteArray(),
            envelope.release.entry("zeta")?.metadataBytes,
        )
        assertEquals(RemoteConfigSnapshotApplyPolicy.Immediate, envelope.release.entry("zeta")?.applyPolicy)
    }

    @Test
    fun `strong ETag must be exact lowercase SHA256 over the exact body`() {
        val body = validBody().encodeToByteArray()
        val valid = strongETag(body)

        for (etag in listOf(
            "W/$valid",
            valid.uppercase(),
            valid.removeSurrounding("\""),
            " $valid",
            "$valid ",
            "$valid,$valid",
            "\"${"a".repeat(63)}\"",
        )) {
            assertNull(etag, parser.parse(body, etag, expectation))
        }
        assertNull(parser.parse(body + ' '.code.toByte(), valid, expectation))
        assertNotNull(parser.parse(body + ' '.code.toByte(), strongETag(body + ' '.code.toByte()), expectation))
    }

    @Test
    fun `input byte and UTF8 limits are enforced before semantic admission`() {
        assertNull(parser.parse(ByteArray(REMOTE_CONFIG_SNAPSHOT_ENVELOPE_MAX_BYTES + 1), "invalid", expectation))
        val invalidUtf8 = validBody().encodeToByteArray().clone().also { bytes ->
            bytes[bytes.indexOf('v'.code.toByte())] = 0xff.toByte()
        }

        assertNull(parser.parse(invalidUtf8, strongETag(invalidUtf8), expectation))
    }

    @Test
    fun `expected project environment and context fingerprint are exact admission boundaries`() {
        val body = validBody()
        val mismatches = listOf(
            expectation.copy(projectId = 43),
            expectation.copy(environmentUid = "env-staging"),
            expectation.copy(contextFingerprint = "b".repeat(64)),
        )

        mismatches.forEach { mismatch -> assertNull(parse(body, mismatch)) }
        assertNull(parse(body.replace("\"project_id\":42", "\"project_id\":43")))
        assertNull(parse(body.replace("env-production", "env-staging")))
        assertNull(parse(body.replace("a".repeat(64), "b".repeat(64))))
    }

    @Test
    fun `complete envelope rejects missing false unknown and duplicate members`() {
        val body = validBody()
        val malicious = listOf(
            body.replace("\"complete_key_set\":true,", ""),
            body.replace("\"complete_key_set\":true", "\"complete_key_set\":false"),
            body.replace("\"schema_version\":1", "\"unknown\":0,\"schema_version\":1"),
            body.replace("\"schema_version\":1", "\"schema_version\":1,\"schema_version\":1"),
            body.replace("\"schema_version\":1", "\"schema_version\":1,\"schema_\\u0076ersion\":1"),
            "$body{}",
        )

        malicious.forEach { json -> assertNull(json, parse(json)) }
    }

    @Test
    fun `value members and logical keys reject unknown duplicates omissions and overflow`() {
        val body = validBody()
        val malicious = listOf(
            body.replace("\"raw\":true", "\"unknown\":null,\"raw\":true"),
            body.replace("\"raw\":true", "\"raw\":false,\"raw\":true"),
            body.replace("\"raw\":true,", ""),
            body.replace("\"variation_uid\":\"variation\"", "\"variation_uid\":\"variation\",\"variation_uid\":\"other\""),
            body.replace("\"only\":", "\"only\":${item()},\"\\u006fnly\":"),
            body.replace("\"only\"", "\"${"k".repeat(257)}\""),
        )

        malicious.forEach { json -> assertNull(json, parse(json)) }

        val tooMany = (0..REMOTE_CONFIG_SNAPSHOT_MAX_KEYS_FOR_TEST).joinToString(",") { index ->
            "\"key-$index\":${item()}"
        }
        assertNull(parse(validBody(values = tooMany)))
    }

    @Test
    fun `raw and metadata preserve whitespace and enforce portable JSON profile`() {
        val exact = parse(validBody(values = "\"only\":${item(" { \"a\" : 1.0 } \n", " [ true ] ")}"))
        assertArrayEquals(" { \"a\" : 1.0 } \n".encodeToByteArray(), exact?.release?.entry("only")?.rawValueBytes)
        assertArrayEquals(" [ true ] ".encodeToByteArray(), exact?.release?.entry("only")?.metadataBytes)

        val invalidRaw = listOf(
            "{\"duplicate\":1,\"duplicate\":2}",
            "{\"a\":1,\"\\u0061\":2}",
            "9007199254740992",
            "-9007199254740992",
            "1e400",
            "\"\\uD800\"",
            "[".repeat(REMOTE_CONFIG_JSON_MAX_DEPTH_FOR_TEST + 1) +
                "null" + "]".repeat(REMOTE_CONFIG_JSON_MAX_DEPTH_FOR_TEST + 1),
        )
        invalidRaw.forEach { raw -> assertNull(raw, parse(validBody(values = "\"only\":${item(raw)}"))) }
        invalidRaw.forEach { metadata ->
            assertNull(metadata, parse(validBody(values = "\"only\":${item(metadata = metadata)}")))
        }
    }

    @Test
    fun `per value metadata and aggregate release byte budgets fail closed`() {
        val oversizedRaw = "\"${"r".repeat(64 * 1024 - 1)}\""
        val oversizedMetadata = "\"${"m".repeat(4 * 1024 - 1)}\""
        assertNull(parse(validBody(values = "\"only\":${item(raw = oversizedRaw)}")))
        assertNull(parse(validBody(values = "\"only\":${item(metadata = oversizedMetadata)}")))

        val nearMaximumRaw = "\"${"v".repeat(64 * 1024 - 2)}\""
        val aggregateOverflow = (0 until 65).joinToString(",") { index ->
            "\"key-$index\":${item(raw = nearMaximumRaw, variationUid = "variation-$index")}" 
        }
        assertNull(parse(validBody(values = aggregateOverflow)))
    }

    @Test
    fun `recursive scanner stops at raw and metadata byte budgets`() {
        val maliciousMembers = buildString(1024 * 1024) {
            append('{')
            repeat(80_000) { index ->
                if (index > 0) append(',')
                append("\"k")
                append(index)
                append("\":0")
            }
            append('}')
        }.encodeToByteArray()

        val rawScan = scanPortableRemoteConfigJson(maliciousMembers, 64 * 1024)
        val metadataScan = scanPortableRemoteConfigJson(maliciousMembers, 4 * 1024)

        assertEquals(false, rawScan.accepted)
        assertEquals(false, metadataScan.accepted)
        assertEquals(64 * 1024, rawScan.consumedBytes)
        assertEquals(4 * 1024, metadataScan.consumedBytes)
    }

    @Test
    fun `raw and metadata spans accept exact byte limit and reject one trailing whitespace over`() {
        val exactRaw = "true" + " ".repeat(64 * 1024 - "true".length)
        val exactMetadata = "null" + " ".repeat(4 * 1024 - "null".length)
        val exact = parse(validBody(values = "\"only\":${item(exactRaw, exactMetadata)}"))

        assertArrayEquals(exactRaw.encodeToByteArray(), exact?.release?.entry("only")?.rawValueBytes)
        assertArrayEquals(exactMetadata.encodeToByteArray(), exact?.release?.entry("only")?.metadataBytes)
        assertNull(
            parse(
                validBody(
                    values = "\"only\":${item(raw = "$exactRaw ", metadata = exactMetadata)}",
                ),
            ),
        )
        assertNull(
            parse(
                validBody(
                    values = "\"only\":${item(raw = exactRaw, metadata = "$exactMetadata ")}",
                ),
            ),
        )
    }

    @Test
    fun `release identifiers numbers hashes and item enums match server bounds`() {
        val body = validBody()
        val malicious = listOf(
            body.replace("\"release_uid\":\"release\"", "\"release_uid\":\"${"r".repeat(37)}\""),
            body.replace("\"release_number\":7", "\"release_number\":0"),
            body.replace("\"release_number\":7", "\"release_number\":9007199254740992"),
            body.replace(HASH, HASH.uppercase()),
            body.replace(HASH, "0".repeat(64)),
            body.replace("\"variation_uid\":\"variation\"", "\"variation_uid\":\"${"v".repeat(37)}\""),
            body.replace("on_next_activate", "unknown"),
        )

        malicious.forEach { json -> assertNull(json, parse(json)) }
    }

    private fun parse(
        json: String,
        expected: RemoteConfigSnapshotEnvelopeExpectation = expectation,
    ): RemoteConfigSnapshotEnvelope? {
        val body = json.encodeToByteArray()
        return parser.parse(body, strongETag(body), expected)
    }

    private fun validBody(values: String = "\"only\":${item()}") =
        "{\"schema_version\":1,\"project_id\":42,\"environment_uid\":\"env-production\"," +
            "\"release_uid\":\"release\",\"release_number\":7,\"manifest_content_hash\":\"$HASH\"," +
            "\"complete_key_set\":true,\"context_fingerprint\":\"${"a".repeat(64)}\"," +
            "\"values\":{$values}}"

    private fun item(
        raw: String = "true",
        metadata: String = "null",
        variationUid: String = "variation",
    ) = "{\"raw\":$raw,\"variation_uid\":\"$variationUid\"," +
        "\"apply_policy\":\"on_next_activate\",\"metadata\":$metadata}"

    private fun strongETag(body: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(body)
        .joinToString(prefix = "\"", postfix = "\"", separator = "") { byte -> "%02x".format(byte) }

    private companion object {
        const val GOLDEN_ETAG = "\"d0c95fec2b0842242efdb0b8a034346538b7db97d6c5ff901501534ed940e3d3\""
        const val HASH = "05b3abf2579a5eb66403cd78be557fd860633a1fe2103c7642030defe32c657f"
        const val REMOTE_CONFIG_SNAPSHOT_MAX_KEYS_FOR_TEST = 1_000
        const val REMOTE_CONFIG_JSON_MAX_DEPTH_FOR_TEST = 64
    }
}
