package com.qonversion.android.sdk.internal.remoteconfig

import com.squareup.moshi.JsonReader
import okio.Buffer
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

internal const val REMOTE_CONFIG_SNAPSHOT_ENVELOPE_MAX_BYTES = 8 * 1024 * 1024

private const val REMOTE_CONFIG_SNAPSHOT_SCHEMA_VERSION = 1
private const val REMOTE_CONFIG_SNAPSHOT_MAX_KEYS = 1_000
private const val REMOTE_CONFIG_SNAPSHOT_VALUE_MAX_BYTES = 64 * 1024
private const val REMOTE_CONFIG_SNAPSHOT_METADATA_MAX_BYTES = 4 * 1024
private const val REMOTE_CONFIG_SNAPSHOT_LOGICAL_KEY_MAX_BYTES = 256
private const val REMOTE_CONFIG_SNAPSHOT_UID_MAX_CODE_POINTS = 36
private const val REMOTE_CONFIG_SNAPSHOT_JSON_MAX_DEPTH = 64
private const val PORTABLE_JSON_MAX_INTEGER = 9_007_199_254_740_991L
private val PORTABLE_JSON_MAX_INTEGER_BIG = BigInteger.valueOf(PORTABLE_JSON_MAX_INTEGER)
private val PORTABLE_JSON_MIN_INTEGER_BIG = PORTABLE_JSON_MAX_INTEGER_BIG.negate()
private val LOWERCASE_SHA256_PATTERN = Regex("^[0-9a-f]{64}$")

/**
 * The addressing an envelope must match to be admitted: exactly the environment the SDK was
 * configured for and the project its gateway session was minted for.
 *
 * [projectId] is learned, not configured: the session bootstrap is the SDK's only source for it,
 * the first bootstrap of a scope pins it, and a later disagreement is refused before a snapshot is
 * ever read (see [RemoteConfigProjectIdRegistry]).
 *
 * The targeting context is deliberately absent. The fingerprint hashes mutable targeting context
 * (app/OS version, locale, purchases, properties); it rotates legitimately and MUST NOT be pinned
 * across fetches. Identity isolation is the session's job — the snapshot read travels on a session
 * token minted for one identity and the gateway routes on it — so the parser validates the
 * fingerprint's *shape* and carries it through as an opaque per-response tag, and nothing anywhere
 * compares it against a previous response's value.
 */
internal data class RemoteConfigSnapshotEnvelopeExpectation(
    val projectId: Long,
    val environmentUid: String,
)

internal class RemoteConfigSnapshotEnvelope internal constructor(
    val projectId: Long,
    val environmentUid: String,
    val contextFingerprint: String,
    val release: RemoteConfigSnapshotRelease,
    val etag: String,
    val bodyDigest: String,
    canonicalBody: ByteArray,
) {
    private val storedCanonicalBody = canonicalBody.clone()
    val canonicalBodyBytes: ByteArray get() = storedCanonicalBody.clone()
}

internal fun interface RemoteConfigSnapshotEnvelopeDecoder {
    fun parse(
        body: ByteArray,
        etag: String,
        expectation: RemoteConfigSnapshotEnvelopeExpectation,
    ): RemoteConfigSnapshotEnvelope?
}

internal class RemoteConfigSnapshotEnvelopeParser : RemoteConfigSnapshotEnvelopeDecoder {
    override fun parse(
        body: ByteArray,
        etag: String,
        expectation: RemoteConfigSnapshotEnvelopeExpectation,
    ): RemoteConfigSnapshotEnvelope? {
        if (!expectation.isValid()) return null
        return parseBoundBody(body, etag)?.takeIf { envelope ->
            envelope.projectId == expectation.projectId &&
                envelope.environmentUid == expectation.environmentUid
        }
    }

    @Suppress("ComplexCondition", "ComplexMethod", "ReturnCount", "SwallowedException")
    internal fun parseBoundBody(
        body: ByteArray,
        etag: String,
    ): RemoteConfigSnapshotEnvelope? {
        if (body.isEmpty() || body.size > REMOTE_CONFIG_SNAPSHOT_ENVELOPE_MAX_BYTES) return null
        val bodyDigest = remoteConfigStrongETagDigest(body, etag) ?: return null
        if (!body.isStrictUtf8()) return null

        return try {
            val decoded = SnapshotJsonReader(body).readEnvelope()
            if (decoded.schemaVersion != REMOTE_CONFIG_SNAPSHOT_SCHEMA_VERSION ||
                decoded.projectId !in 1..PORTABLE_JSON_MAX_INTEGER ||
                !decoded.environmentUid.isValidUid() ||
                !LOWERCASE_SHA256_PATTERN.matches(decoded.contextFingerprint) ||
                !decoded.completeKeySet ||
                !decoded.releaseUid.isValidUid() ||
                decoded.releaseNumber !in 1..PORTABLE_JSON_MAX_INTEGER ||
                !LOWERCASE_SHA256_PATTERN.matches(decoded.manifestContentHash) ||
                decoded.manifestContentHash.all { it == '0' }
            ) {
                return null
            }
            val release = RemoteConfigSnapshotRelease(
                releaseUid = decoded.releaseUid,
                releaseNumber = decoded.releaseNumber,
                manifestContentHash = decoded.manifestContentHash,
                entries = decoded.values.map { value ->
                    RemoteConfigSnapshotEntry.value(
                        key = value.key,
                        rawValue = value.raw,
                        variationUid = value.variationUid,
                        applyPolicy = value.applyPolicy,
                        metadata = value.metadata,
                    )
                },
                canonicalBody = body,
                strongETag = etag,
                contextFingerprint = decoded.contextFingerprint,
            )
            RemoteConfigSnapshotEnvelope(
                projectId = decoded.projectId,
                environmentUid = decoded.environmentUid,
                contextFingerprint = decoded.contextFingerprint,
                release = release,
                etag = etag,
                bodyDigest = bodyDigest,
                canonicalBody = body,
            )
        } catch (_: Exception) {
            null
        }
    }
}

private data class DecodedSnapshotEnvelope(
    val schemaVersion: Int,
    val projectId: Long,
    val environmentUid: String,
    val releaseUid: String,
    val releaseNumber: Long,
    val manifestContentHash: String,
    val completeKeySet: Boolean,
    val contextFingerprint: String,
    val values: List<DecodedSnapshotValue>,
)

private data class DecodedSnapshotValue(
    val key: String,
    val raw: ByteArray,
    val variationUid: String,
    val applyPolicy: RemoteConfigSnapshotApplyPolicy,
    val metadata: ByteArray,
)

internal data class RemoteConfigPortableJsonScanResult(
    val accepted: Boolean,
    val consumedBytes: Int,
)

internal fun scanPortableRemoteConfigJson(
    bytes: ByteArray,
    maxBytes: Int,
): RemoteConfigPortableJsonScanResult {
    require(maxBytes > 0)
    val reader = SnapshotJsonReader(bytes)
    val accepted = try {
        reader.scanPortableJson(maxBytes)
        true
    } catch (_: Exception) {
        false
    }
    return RemoteConfigPortableJsonScanResult(accepted, reader.cursorOffset)
}

private class SnapshotJsonReader(private val bytes: ByteArray) {
    private var index = 0
    private var cursorLimit = bytes.size
    val cursorOffset: Int get() = index

    fun scanPortableJson(maxBytes: Int): ByteArray {
        val value = readPortableJsonBytes(maxBytes)
        require(index == bytes.size) { "trailing JSON data" }
        return value
    }

    @Suppress("ComplexMethod")
    fun readEnvelope(): DecodedSnapshotEnvelope {
        var schemaVersion: Int? = null
        var projectId: Long? = null
        var environmentUid: String? = null
        var releaseUid: String? = null
        var releaseNumber: Long? = null
        var manifestContentHash: String? = null
        var completeKeySet: Boolean? = null
        var contextFingerprint: String? = null
        var values: List<DecodedSnapshotValue>? = null
        val members = mutableSetOf<String>()

        skipWhitespace()
        expect('{')
        skipWhitespace()
        if (!consume('}')) {
            while (true) {
                val name = readString()
                require(members.add(name)) { "duplicate envelope member" }
                skipWhitespace()
                expect(':')
                when (name) {
                    "schema_version" -> schemaVersion = readExactInt()
                    "project_id" -> projectId = readExactLong()
                    "environment_uid" -> environmentUid = readStringValue()
                    "release_uid" -> releaseUid = readStringValue()
                    "release_number" -> releaseNumber = readExactLong()
                    "manifest_content_hash" -> manifestContentHash = readStringValue()
                    "complete_key_set" -> completeKeySet = readBooleanValue()
                    "context_fingerprint" -> contextFingerprint = readStringValue()
                    "values" -> values = readValues()
                    else -> error("unknown envelope member")
                }
                skipWhitespace()
                if (consume('}')) break
                expect(',')
                skipWhitespace()
            }
        }
        skipWhitespace()
        require(index == bytes.size) { "trailing envelope data" }
        return DecodedSnapshotEnvelope(
            schemaVersion = requireNotNull(schemaVersion),
            projectId = requireNotNull(projectId),
            environmentUid = requireNotNull(environmentUid),
            releaseUid = requireNotNull(releaseUid),
            releaseNumber = requireNotNull(releaseNumber),
            manifestContentHash = requireNotNull(manifestContentHash),
            completeKeySet = requireNotNull(completeKeySet),
            contextFingerprint = requireNotNull(contextFingerprint),
            values = requireNotNull(values),
        )
    }

    private fun readValues(): List<DecodedSnapshotValue> {
        val values = mutableListOf<DecodedSnapshotValue>()
        val keys = mutableSetOf<String>()
        skipWhitespace()
        expect('{')
        skipWhitespace()
        if (consume('}')) return values
        while (true) {
            require(values.size < REMOTE_CONFIG_SNAPSHOT_MAX_KEYS) { "too many values" }
            val key = readString()
            require(key.isNotEmpty() && key.toByteArray(StandardCharsets.UTF_8).size <=
                REMOTE_CONFIG_SNAPSHOT_LOGICAL_KEY_MAX_BYTES) { "invalid logical key" }
            require(keys.add(key)) { "duplicate logical key" }
            skipWhitespace()
            expect(':')
            values += readSnapshotValue(key)
            skipWhitespace()
            if (consume('}')) break
            expect(',')
            skipWhitespace()
        }
        return values
    }

    @Suppress("ComplexMethod")
    private fun readSnapshotValue(key: String): DecodedSnapshotValue {
        var raw: ByteArray? = null
        var variationUid: String? = null
        var applyPolicy: RemoteConfigSnapshotApplyPolicy? = null
        var metadata: ByteArray? = null
        val members = mutableSetOf<String>()
        skipWhitespace()
        expect('{')
        skipWhitespace()
        require(!consume('}')) { "empty snapshot value" }
        while (true) {
            val name = readString()
            require(members.add(name)) { "duplicate snapshot value member" }
            skipWhitespace()
            expect(':')
            when (name) {
                "raw" -> raw = readPortableJsonBytes(REMOTE_CONFIG_SNAPSHOT_VALUE_MAX_BYTES)
                "variation_uid" -> variationUid = readStringValue()
                "apply_policy" -> applyPolicy = when (readStringValue()) {
                    "on_next_activate" -> RemoteConfigSnapshotApplyPolicy.OnNextActivate
                    "immediate" -> RemoteConfigSnapshotApplyPolicy.Immediate
                    else -> error("unsupported apply policy")
                }
                "metadata" -> metadata = readPortableJsonBytes(REMOTE_CONFIG_SNAPSHOT_METADATA_MAX_BYTES)
                else -> error("unknown snapshot value member")
            }
            skipWhitespace()
            if (consume('}')) break
            expect(',')
            skipWhitespace()
        }
        return DecodedSnapshotValue(
            key = key,
            raw = requireNotNull(raw),
            variationUid = requireNotNull(variationUid).also { require(it.isValidUid()) },
            applyPolicy = requireNotNull(applyPolicy),
            metadata = requireNotNull(metadata),
        )
    }

    private fun readPortableJsonBytes(maxBytes: Int): ByteArray {
        val start = index
        val enclosingLimit = cursorLimit
        val valueLimit = minOf(enclosingLimit, start + maxBytes)
        cursorLimit = valueLimit
        val value = try {
            skipWhitespace()
            readPortableJsonValue(depth = 1)
            skipWhitespace()
            require(index > start) { "empty JSON value" }
            bytes.copyOfRange(start, index)
        } finally {
            cursorLimit = enclosingLimit
        }
        if (index == valueLimit && valueLimit < enclosingLimit) {
            require(peekCharacter() == ',' || peekCharacter() == '}' || peekCharacter() == ']') {
                "JSON value exceeds byte limit"
            }
        }
        return value
    }

    @Suppress("ReturnCount")
    private fun readPortableJsonValue(depth: Int) {
        when (peekCharacter()) {
            '{' -> {
                require(depth <= REMOTE_CONFIG_SNAPSHOT_JSON_MAX_DEPTH) { "JSON is too deep" }
                expect('{')
                skipWhitespace()
                val members = mutableSetOf<String>()
                if (consume('}')) return
                while (true) {
                    val name = readString()
                    require(members.add(name)) { "duplicate JSON member" }
                    skipWhitespace()
                    expect(':')
                    skipWhitespace()
                    readPortableJsonValue(depth + 1)
                    skipWhitespace()
                    if (consume('}')) return
                    expect(',')
                    skipWhitespace()
                }
            }
            '[' -> {
                require(depth <= REMOTE_CONFIG_SNAPSHOT_JSON_MAX_DEPTH) { "JSON is too deep" }
                expect('[')
                skipWhitespace()
                if (consume(']')) return
                while (true) {
                    readPortableJsonValue(depth + 1)
                    skipWhitespace()
                    if (consume(']')) return
                    expect(',')
                    skipWhitespace()
                }
            }
            '"' -> readString()
            't' -> expectLiteral("true")
            'f' -> expectLiteral("false")
            'n' -> expectLiteral("null")
            else -> validatePortableNumber(readNumber())
        }
    }

    private fun readStringValue(): String {
        skipWhitespace()
        return readString()
    }

    private fun readBooleanValue(): Boolean {
        skipWhitespace()
        return when (peekCharacter()) {
            't' -> true.also { expectLiteral("true") }
            'f' -> false.also { expectLiteral("false") }
            else -> error("expected boolean")
        }
    }

    private fun readExactInt(): Int {
        val value = readExactLong()
        require(value in Int.MIN_VALUE..Int.MAX_VALUE)
        return value.toInt()
    }

    private fun readExactLong(): Long {
        skipWhitespace()
        val token = readNumber()
        require(token.none { it == '.' || it == 'e' || it == 'E' }) { "expected integer" }
        require(token.length <= PORTABLE_JSON_MAX_INTEGER_TOKEN_LENGTH) { "integer token is too long" }
        val integer = BigInteger(token)
        require(integer >= PORTABLE_JSON_MIN_INTEGER_BIG && integer <= PORTABLE_JSON_MAX_INTEGER_BIG)
        return integer.toLong()
    }

    @Suppress("NestedBlockDepth")
    private fun readString(): String {
        val start = index
        expect('"')
        var escaped = false
        while (index < cursorLimit) {
            val byte = bytes[index].toInt() and BYTE_MASK
            index++
            if (escaped) {
                when (byte.toChar()) {
                    '"', '\\', '/', 'b', 'f', 'n', 'r', 't' -> Unit
                    'u' -> repeat(JSON_UNICODE_ESCAPE_HEX_DIGITS) {
                        require(index < cursorLimit && (bytes[index].toInt() and BYTE_MASK).isHexDigit())
                        index++
                    }
                    else -> error("invalid JSON escape")
                }
                escaped = false
            } else {
                when {
                    byte == '"'.code -> {
                        val quoted = bytes.copyOfRange(start, index)
                        val reader = JsonReader.of(Buffer().write(quoted)).apply { isLenient = false }
                        return reader.nextString().also {
                            require(reader.peek() == JsonReader.Token.END_DOCUMENT)
                            require(it.hasValidSurrogatePairs()) { "unpaired JSON string surrogate" }
                        }
                    }
                    byte == '\\'.code -> escaped = true
                    byte < JSON_CONTROL_CHARACTER_LIMIT -> error("unescaped JSON control character")
                }
            }
        }
        error("unterminated JSON string")
    }

    private fun readNumber(): String {
        val start = index
        consume('-')
        when {
            consume('0') -> require(!peekCharacterOrNull().isDigit()) { "leading zero" }
            peekCharacter() in '1'..'9' -> while (peekCharacterOrNull().isDigit()) index++
            else -> error("invalid JSON number")
        }
        if (consume('.')) {
            require(peekCharacterOrNull().isDigit()) { "missing fraction" }
            while (peekCharacterOrNull().isDigit()) index++
        }
        if (peekCharacterOrNull() == 'e' || peekCharacterOrNull() == 'E') {
            index++
            if (peekCharacterOrNull() == '+' || peekCharacterOrNull() == '-') index++
            require(peekCharacterOrNull().isDigit()) { "missing exponent" }
            while (peekCharacterOrNull().isDigit()) index++
        }
        return bytes.copyOfRange(start, index).toString(StandardCharsets.US_ASCII)
    }

    private fun expectLiteral(literal: String) {
        for (character in literal) expect(character)
    }

    private fun expect(character: Char) {
        require(index < cursorLimit && bytes[index].toInt() and BYTE_MASK == character.code) {
            "expected $character"
        }
        index++
    }

    private fun consume(character: Char): Boolean {
        if (index >= cursorLimit || bytes[index].toInt() and BYTE_MASK != character.code) return false
        index++
        return true
    }

    private fun skipWhitespace() {
        while (index < cursorLimit && when (bytes[index].toInt() and BYTE_MASK) {
                ' '.code, '\t'.code, '\r'.code, '\n'.code -> true
                else -> false
            }
        ) {
            index++
        }
    }

    private fun peekCharacter(): Char = peekCharacterOrNull() ?: error("unexpected end of JSON")

    private fun peekCharacterOrNull(): Char? =
        if (index < cursorLimit) (bytes[index].toInt() and BYTE_MASK).toChar() else null
}

private fun RemoteConfigSnapshotEnvelopeExpectation.isValid(): Boolean =
    projectId in 1..PORTABLE_JSON_MAX_INTEGER && environmentUid.isValidUid()

private fun String.isValidUid(): Boolean =
    isNotEmpty() && hasValidSurrogatePairs() && codePointCount(0, length) <= REMOTE_CONFIG_SNAPSHOT_UID_MAX_CODE_POINTS

@Suppress("ReturnCount")
private fun String.hasValidSurrogatePairs(): Boolean {
    var index = 0
    while (index < length) {
        when {
            this[index].isHighSurrogate() -> {
                if (index + 1 >= length || !this[index + 1].isLowSurrogate()) return false
                index += 2
            }
            this[index].isLowSurrogate() -> return false
            else -> index++
        }
    }
    return true
}

private fun validatePortableNumber(token: String) {
    if (token.none { it == '.' || it == 'e' || it == 'E' }) {
        require(token.length <= PORTABLE_JSON_MAX_INTEGER_TOKEN_LENGTH) { "integer token is too long" }
        val integer = BigInteger(token)
        require(integer >= PORTABLE_JSON_MIN_INTEGER_BIG && integer <= PORTABLE_JSON_MAX_INTEGER_BIG) {
            "JSON integer is outside the portable range"
        }
    }
    require(token.toDouble().isFinite()) { "JSON number is not finite binary64" }
}

internal fun remoteConfigStrongETagDigest(body: ByteArray, etag: String): String? {
    val digest = etag
        .takeIf { it.length == SHA256_ETAG_LENGTH && it.first() == '"' && it.last() == '"' }
        ?.substring(1, etag.length - 1)
        ?.takeIf(LOWERCASE_SHA256_PATTERN::matches)
        ?: return null
    return digest.takeIf { body.sha256Hex() == it }
}

private fun ByteArray.isStrictUtf8(): Boolean = try {
    StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(this))
    true
} catch (_: Exception) {
    false
}

private fun ByteArray.sha256Hex(): String = MessageDigest.getInstance("SHA-256").digest(this).toLowercaseHex()

private fun ByteArray.toLowercaseHex(): String = buildString(size * 2) {
    for (byte in this@toLowercaseHex) {
        val value = byte.toInt() and BYTE_MASK
        append(HEX[value ushr HEX_HIGH_NIBBLE_SHIFT])
        append(HEX[value and HEX_NIBBLE_MASK])
    }
}

private fun Char?.isDigit(): Boolean = this != null && this in '0'..'9'
private fun Int.isHexDigit(): Boolean = this in '0'.code..'9'.code || this in 'a'.code..'f'.code ||
    this in 'A'.code..'F'.code

private const val BYTE_MASK = 0xff
private const val JSON_CONTROL_CHARACTER_LIMIT = 0x20
private const val HEX_HIGH_NIBBLE_SHIFT = 4
private const val HEX_NIBBLE_MASK = 0x0f
private const val HEX = "0123456789abcdef"
private const val SHA256_ETAG_LENGTH = 66
private const val PORTABLE_JSON_MAX_INTEGER_TOKEN_LENGTH = 17
private const val JSON_UNICODE_ESCAPE_HEX_DIGITS = 4
