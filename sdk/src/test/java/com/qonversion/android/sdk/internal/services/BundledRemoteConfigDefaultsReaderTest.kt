package com.qonversion.android.sdk.internal.services

import android.content.Context
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

internal class BundledRemoteConfigDefaultsReaderTest {
    private val context = io.mockk.mockk<Context>(relaxed = true)

    @After
    fun resetProcessCache() {
        BundledRemoteConfigDefaults.resetForTests()
    }

    @Test
    fun `server golden parses with exact raw bytes and header`() {
        val goldenBytes = SERVER_GOLDEN_ARTIFACT.toByteArray()
        assertEquals(
            SERVER_GOLDEN_ARTIFACT_SHA256,
            MessageDigest.getInstance("SHA-256").digest(goldenBytes).joinToString("") { "%02x".format(it) },
        )
        val document = reader(goldenBytes).read(context)

        assertNotNull(document)
        requireNotNull(document)
        assertEquals(42L, document.projectId)
        assertEquals("env-production", document.environmentUid)
        assertEquals("release-portable", document.releaseUid)
        assertEquals(7L, document.releaseNumber)
        assertEquals(SERVER_GOLDEN_MANIFEST_CONTENT_HASH, document.manifestContentHash)
        assertEquals(SERVER_GOLDEN_DEFAULTS_DIGEST, document.defaultsDigest)
        assertEquals(
            " { \"message\": \"Привет 👋\" } \n".toByteArray().toList(),
            requireNotNull(document.defaultFor("alpha")).rawJsonBytes.toList(),
        )
        assertEquals("variation-alpha", document.defaultFor("alpha")?.variationUid)
        assertEquals("null", String(requireNotNull(document.defaultFor("beta")).rawJsonBytes))
    }

    @Test
    fun `all JSON value kinds and Unicode are exposed including wrapped null`() {
        val cache = BundledRemoteConfigDefaultsCache(reader(SERVER_ALL_TYPES_ARTIFACT.toByteArray()))

        assertEquals(listOf(1.0, "two", false), cache.value(context, "array")?.rawValue)
        assertEquals(true, cache.value(context, "bool")?.rawValue)
        val nullValue = cache.value(context, "null")
        assertNotNull("a present JSON null must be distinguishable from a missing key", nullValue)
        assertNull(nullValue?.rawValue)
        assertEquals(42.5, cache.value(context, "number")?.rawValue)
        assertEquals(
            mapOf("enabled" to true, "nested" to "value"),
            cache.value(context, "object")?.rawValue,
        )
        assertEquals("hello", cache.value(context, "string")?.rawValue)
        assertEquals("Привет 👋", cache.value(context, "unicode")?.rawValue)
        assertNull(cache.value(context, "missing"))
    }

    @Test
    fun `corrupt or noncanonical artifacts fail closed`() {
        val valid = String(artifact(listOf(default("alpha", "true"), default("beta", "null"))))
        val invalidCases = mapOf(
            "digest" to valid.replace(
                Regex("\\\"defaultsDigest\\\":\\\"[0-9a-f]{64}\\\""),
                "\"defaultsDigest\":\"${"0".repeat(64)}\"",
            ).toByteArray(),
            "base64" to valid.replace(
                Regex("\\\"valueBase64\\\":\\\"[^\\\"]+\\\""),
                "\"valueBase64\":\"***\"",
            ).toByteArray(),
            "json" to artifact(listOf(default("alpha", "not-json"))),
            "schema" to artifact(listOf(default("alpha", "true")), schemaVersion = 2),
            "manifest hash uppercase" to valid.replace(
                SERVER_GOLDEN_MANIFEST_CONTENT_HASH,
                SERVER_GOLDEN_MANIFEST_CONTENT_HASH.uppercase(),
            ).toByteArray(),
            "unsorted" to artifact(listOf(default("beta", "null"), default("alpha", "true"))),
            "duplicate" to artifact(listOf(default("alpha", "true"), default("alpha", "false"))),
            "missing field" to valid.replace(Regex(",\\\"releaseUid\\\":\\\"[^\\\"]+\\\""), "").toByteArray(),
            "unknown field" to valid.replaceFirst("{", "{\"unknown\":true,").toByteArray(),
            "duplicate field" to valid.replace(
                "{\"schemaVersion\":1,",
                "{\"schemaVersion\":1,\"schemaVersion\":1,",
            ).toByteArray(),
            "noncanonical whitespace" to " $valid".toByteArray(),
            "legacy fallback" to "{\"remote_config_list\":[]}".toByteArray(),
        )

        invalidCases.forEach { (name, bytes) ->
            assertNull(name, reader(bytes).read(context))
        }
    }

    @Test
    fun `header identifiers and collection bounds are enforced`() {
        val validDefault = default("alpha", "true")
        val invalidCases = mapOf(
            "zero project id" to artifact(listOf(validDefault), projectId = 0),
            "negative project id" to artifact(listOf(validDefault), projectId = -1),
            "zero release number" to artifact(listOf(validDefault), releaseNumber = 0),
            "negative release number" to artifact(listOf(validDefault), releaseNumber = -1),
            "empty environment uid" to artifact(listOf(validDefault), environmentUid = ""),
            "long environment uid" to artifact(listOf(validDefault), environmentUid = "e".repeat(37)),
            "empty release uid" to artifact(listOf(validDefault), releaseUid = ""),
            "long release uid" to artifact(listOf(validDefault), releaseUid = "r".repeat(37)),
            "empty key" to artifact(listOf(DefaultFixture("", "variation", "true".toByteArray()))),
            "key over 256 UTF-8 bytes" to artifact(
                listOf(DefaultFixture("é".repeat(129), "variation", "true".toByteArray())),
            ),
            "empty variation uid" to artifact(
                listOf(DefaultFixture("alpha", "", "true".toByteArray())),
            ),
            "long variation uid" to artifact(
                listOf(DefaultFixture("alpha", "v".repeat(37), "true".toByteArray())),
            ),
            "more than 1000 defaults" to artifact(
                List(1_001) { index -> default("key-${index.toString().padStart(4, '0')}", "true") },
            ),
        )

        invalidCases.forEach { (name, bytes) ->
            assertNull(name, reader(bytes).read(context))
        }

        assertNotNull(
            "UID limits are measured in Unicode code points",
            reader(artifact(listOf(validDefault), environmentUid = "😀".repeat(36))).read(context),
        )
        assertNotNull(
            "key limit is inclusive and measured in UTF-8 bytes",
            reader(
                artifact(listOf(DefaultFixture("é".repeat(128), "variation", "true".toByteArray()))),
            ).read(context),
        )
    }

    @Test
    fun `default entries reject missing duplicate unknown and noncanonical fields`() {
        val valid = String(artifact(listOf(default("alpha", "true"))))
        val invalidCases = mapOf(
            "missing variation uid" to valid.replace(
                Regex(",\"variationUid\":\"[^\"]+\""),
                "",
            ),
            "duplicate key field" to valid.replace(
                "{\"key\":\"alpha\",",
                "{\"key\":\"alpha\",\"key\":\"alpha\",",
            ),
            "unknown default field" to valid.replace(
                "{\"key\":\"alpha\",",
                "{\"unknown\":true,\"key\":\"alpha\",",
            ),
            "unpadded base64" to valid.replace("dHJ1ZQ==", "dHJ1ZQ"),
        )

        invalidCases.forEach { (name, artifact) ->
            assertNull(name, reader(artifact.toByteArray()).read(context))
        }
    }

    @Test
    fun `invalid UTF-8 and oversized asset fail closed`() {
        val invalidUtf8 = artifact(listOf(default("alpha", "true"))).also {
            it[it.indexOf('a'.code.toByte())] = 0x80.toByte()
        }

        assertNull(reader(invalidUtf8).read(context))
        assertNull(reader(ByteArray(BUNDLED_REMOTE_CONFIG_DEFAULTS_MAX_BYTES + 1) { ' '.code.toByte() }).read(context))
    }

    @Test
    fun `value larger than serving limit and JSON deeper than serving limit fail closed`() {
        val tooLarge = "\"${"x".repeat(BUNDLED_REMOTE_CONFIG_DEFAULT_VALUE_MAX_BYTES)}\""
        val tooDeep = "[".repeat(BUNDLED_REMOTE_CONFIG_DEFAULT_JSON_MAX_DEPTH + 1) +
            "null" + "]".repeat(BUNDLED_REMOTE_CONFIG_DEFAULT_JSON_MAX_DEPTH + 1)

        assertNull(reader(artifact(listOf(default("alpha", tooLarge)))).read(context))
        assertNull(reader(artifact(listOf(default("alpha", tooDeep)))).read(context))
    }

    @Test
    fun `portable number profile accepts shared boundaries and rejects divergence`() {
        val finite = BundledRemoteConfigDefaultsCache(
            reader(SERVER_NUMBER_BOUNDARIES_ARTIFACT.toByteArray()),
        )

        assertEquals(1e308, finite.value(context, "float_max")?.rawValue)
        assertEquals(9_007_199_254_740_991.0, finite.value(context, "int_max")?.rawValue)
        assertEquals(-9_007_199_254_740_991.0, finite.value(context, "int_min")?.rawValue)
        listOf("1e309", "9007199254740992", "-9007199254740992").forEach { number ->
            assertNull(number, reader(artifact(listOf(default("number", number)))).read(context))
        }
        assertNull(
            "project ID outside the portable exact integer range",
            reader(artifact(listOf(default("number", "1")), projectId = 9_007_199_254_740_992L)).read(context),
        )
        assertNull(
            "release number outside the portable exact integer range",
            reader(artifact(listOf(default("number", "1")), releaseNumber = 9_007_199_254_740_992L)).read(context),
        )
    }

    @Test
    fun `escaped surrogate pairs match the producer portable Unicode profile`() {
        val valid = BundledRemoteConfigDefaultsCache(
            reader(SERVER_ESCAPED_SURROGATE_ARTIFACT.toByteArray()),
        )
        assertEquals("👋", valid.value(context, "escaped_pair")?.rawValue)

        listOf("\"\\ud83d\"", "\"\\ud83d\\u0041\"", "\"\\udc4b\"").forEach { raw ->
            assertNull(raw, reader(artifact(listOf(default("unicode", raw)))).read(context))
        }
    }

    @Test
    fun `process cache loads asset once under concurrency and returns immutable values`() {
        val reads = AtomicInteger()
        val source = BundledRemoteConfigDefaultsAssetSource {
            reads.incrementAndGet()
            ByteArrayInputStream(
                artifact(
                    listOf(default("object", "{\"nested\":[{\"value\":1}]}")),
                ),
            )
        }
        val cache = BundledRemoteConfigDefaultsCache(BundledRemoteConfigDefaultsReader(source))
        val executor = Executors.newFixedThreadPool(8)

        val results = executor.invokeAll(
            List(64) { Callable { cache.value(context, "object") } },
        ).map { it.get(5, TimeUnit.SECONDS) }
        executor.shutdownNow()

        assertEquals(1, reads.get())
        assertTrue(results.all { it?.rawValue == mapOf("nested" to listOf(mapOf("value" to 1.0))) })
        val first = requireNotNull(results.first()).rawValue as Map<*, *>
        @Suppress("UNCHECKED_CAST")
        val mutable = first as MutableMap<String, Any?>
        assertThrows(UnsupportedOperationException::class.java) { mutable["mutated"] = true }
        assertEquals(
            mapOf("nested" to listOf(mapOf("value" to 1.0))),
            cache.value(context, "object")?.rawValue,
        )
    }

    @Test
    fun `raw bytes and digest bytes are defensive copies`() {
        val document = requireNotNull(
            reader(artifact(listOf(default("alpha", "true")))).read(context),
        )
        val entry = requireNotNull(document.defaultFor("alpha"))

        entry.rawJsonBytes[0] = 'f'.code.toByte()
        entry.digestBytes()[0] = 'f'.code.toByte()

        assertEquals("true", String(entry.rawJsonBytes))
        assertEquals("true", String(entry.digestBytes()))
    }

    @Test
    fun `invalid artifact is negatively cached`() {
        val reads = AtomicInteger()
        val cache = BundledRemoteConfigDefaultsCache(
            BundledRemoteConfigDefaultsReader(
                BundledRemoteConfigDefaultsAssetSource {
                    reads.incrementAndGet()
                    ByteArrayInputStream("not-json".toByteArray())
                },
            ),
        )

        repeat(10) { assertNull(cache.value(context, "any")) }

        assertEquals(1, reads.get())
    }

    private fun reader(bytes: ByteArray) = BundledRemoteConfigDefaultsReader(
        BundledRemoteConfigDefaultsAssetSource { ByteArrayInputStream(bytes) },
    )

    private data class DefaultFixture(
        val key: String,
        val variationUid: String,
        val value: ByteArray,
    )

    private fun default(key: String, rawJson: String) = DefaultFixture(
        key = key,
        variationUid = "variation-$key",
        value = rawJson.toByteArray(),
    )

    private fun artifact(
        defaults: List<DefaultFixture>,
        schemaVersion: Int = 1,
        projectId: Long = 42,
        environmentUid: String = "env-production",
        releaseUid: String = "release-portable",
        releaseNumber: Long = 7,
        manifestContentHash: String = SERVER_GOLDEN_MANIFEST_CONTENT_HASH,
    ): ByteArray {
        val digest = defaultsDigest(
            schemaVersion,
            projectId,
            environmentUid,
            releaseUid,
            releaseNumber,
            manifestContentHash,
            defaults,
        )
        return buildString {
            append("{\"schemaVersion\":").append(schemaVersion)
            append(",\"projectId\":").append(projectId)
            append(",\"environmentUid\":").append(jsonString(environmentUid))
            append(",\"releaseUid\":").append(jsonString(releaseUid))
            append(",\"releaseNumber\":").append(releaseNumber)
            append(",\"manifestContentHash\":\"").append(manifestContentHash).append('"')
            append(",\"defaultsDigest\":\"").append(digest).append('"')
            append(",\"defaults\":[")
            defaults.forEachIndexed { index, value ->
                if (index > 0) append(',')
                append("{\"key\":").append(jsonString(value.key))
                append(",\"variationUid\":").append(jsonString(value.variationUid))
                append(",\"valueBase64\":\"")
                    .append(Base64.getEncoder().encodeToString(value.value))
                    .append("\"}")
            }
            append("]}")
        }.toByteArray()
    }

    private fun defaultsDigest(
        schemaVersion: Int,
        projectId: Long,
        environmentUid: String,
        releaseUid: String,
        releaseNumber: Long,
        manifestContentHash: String,
        defaults: List<DefaultFixture>,
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        fun part(bytes: ByteArray) {
            digest.update(ByteBuffer.allocate(Long.SIZE_BYTES).putLong(bytes.size.toLong()).array())
            digest.update(bytes)
        }
        fun part(value: String) = part(value.toByteArray(StandardCharsets.UTF_8))

        part("qonversion.remote-config-fallback-defaults.v1")
        part(schemaVersion.toString())
        part(projectId.toString())
        part(environmentUid)
        part(releaseUid)
        part(releaseNumber.toString())
        part(manifestContentHash)
        part(defaults.size.toString())
        defaults.forEach { value ->
            part(value.key)
            part(value.variationUid)
            part(value.value)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun jsonString(value: String): String {
        val buffer = Buffer()
        val writer = com.squareup.moshi.JsonWriter.of(buffer)
        writer.value(value)
        writer.close()
        return buffer.readUtf8()
    }

    private companion object {
        // Replaced only if the server contract golden changes. This is copied
        // byte-for-byte from configurator's fallback_artifact_test.go.
        const val SERVER_GOLDEN_ARTIFACT =
            "{\"schemaVersion\":1,\"projectId\":42,\"environmentUid\":\"env-production\"," +
                "\"releaseUid\":\"release-portable\",\"releaseNumber\":7," +
                "\"manifestContentHash\":\"0291766e896e3f36aca5385082d74e8bd70fabf12ee776b7dfa2cd4d961c9dea\"," +
                "\"defaultsDigest\":\"9e4dfcb4069901c3af4341383c814b24cdc39b20491f7a4593e0036d01f39540\"," +
                "\"defaults\":[{\"key\":\"alpha\",\"variationUid\":\"variation-alpha\"," +
                "\"valueBase64\":\"IHsgIm1lc3NhZ2UiOiAi0J/RgNC40LLQtdGCIPCfkYsiIH0gCg==\"}," +
                "{\"key\":\"beta\",\"variationUid\":\"variation-beta\",\"valueBase64\":\"bnVsbA==\"}]}"
        const val SERVER_GOLDEN_MANIFEST_CONTENT_HASH =
            "0291766e896e3f36aca5385082d74e8bd70fabf12ee776b7dfa2cd4d961c9dea"
        const val SERVER_GOLDEN_DEFAULTS_DIGEST =
            "9e4dfcb4069901c3af4341383c814b24cdc39b20491f7a4593e0036d01f39540"
        const val SERVER_GOLDEN_ARTIFACT_SHA256 =
            "db09d5051c9702773d6dedb4023c8099b20f0cf49daf0471f553c44d4ceaafc5"
        const val SERVER_ALL_TYPES_ARTIFACT =
            "{\"schemaVersion\":1,\"projectId\":42,\"environmentUid\":\"env-production\"," +
                "\"releaseUid\":\"release-all-json-types\",\"releaseNumber\":8," +
                "\"manifestContentHash\":\"aa6a89035cd667e719c826029b46d063107991009382955b2332974fea7e417d\"," +
                "\"defaultsDigest\":\"05237b0c07eac66a5ae1f7268c9afef3f0170b8649d22559388a4071ee81e95f\"," +
                "\"defaults\":[{\"key\":\"array\",\"variationUid\":\"variation-array\",\"valueBase64\":\"WzEsInR3byIsZmFsc2Vd\"}," +
                "{\"key\":\"bool\",\"variationUid\":\"variation-bool\",\"valueBase64\":\"dHJ1ZQ==\"}," +
                "{\"key\":\"null\",\"variationUid\":\"variation-null\",\"valueBase64\":\"bnVsbA==\"}," +
                "{\"key\":\"number\",\"variationUid\":\"variation-number\",\"valueBase64\":\"NDIuNQ==\"}," +
                "{\"key\":\"object\",\"variationUid\":\"variation-object\",\"valueBase64\":\"eyJlbmFibGVkIjp0cnVlLCJuZXN0ZWQiOiJ2YWx1ZSJ9\"}," +
                "{\"key\":\"string\",\"variationUid\":\"variation-string\",\"valueBase64\":\"ImhlbGxvIg==\"}," +
                "{\"key\":\"unicode\",\"variationUid\":\"variation-unicode\",\"valueBase64\":\"ItCf0YDQuNCy0LXRgiDwn5GLIg==\"}]}"
        const val SERVER_NUMBER_BOUNDARIES_ARTIFACT =
            "{\"schemaVersion\":1,\"projectId\":42,\"environmentUid\":\"env-production\"," +
                "\"releaseUid\":\"release-number-boundaries\",\"releaseNumber\":9," +
                "\"manifestContentHash\":\"3896effad7acc9270542cb9c9ce76975c65888a19f6efc6eafba299e8e75be4b\"," +
                "\"defaultsDigest\":\"0f8138237ed0fc124a161e6dcc2f5fe6e3fa78691b0dea2dbf7f42bc66736d8d\"," +
                "\"defaults\":[{\"key\":\"float_max\",\"variationUid\":\"variation-float_max\",\"valueBase64\":\"MWUzMDg=\"}," +
                "{\"key\":\"int_max\",\"variationUid\":\"variation-int_max\",\"valueBase64\":\"OTAwNzE5OTI1NDc0MDk5MQ==\"}," +
                "{\"key\":\"int_min\",\"variationUid\":\"variation-int_min\",\"valueBase64\":\"LTkwMDcxOTkyNTQ3NDA5OTE=\"}]}"
        const val SERVER_ESCAPED_SURROGATE_ARTIFACT =
            "{\"schemaVersion\":1,\"projectId\":42,\"environmentUid\":\"env-production\"," +
                "\"releaseUid\":\"release-escaped-surrogate\",\"releaseNumber\":10," +
                "\"manifestContentHash\":\"e2d2e6cd92b6aca3bae40d4196bcedb6beade7b3dbf672bc13193b3dd9e7ad02\"," +
                "\"defaultsDigest\":\"474f2f24b12594d138755a501c2868553a158201ddc955d1c9f5fc7900c8ce44\"," +
                "\"defaults\":[{\"key\":\"escaped_pair\",\"variationUid\":\"variation-escaped-pair\"," +
                "\"valueBase64\":\"Ilx1ZDgzZFx1ZGM0YiI=\"}]}"
    }
}
