package com.qonversion.android.sdk.internal.services

import android.content.Context
import com.qonversion.android.sdk.dto.QRemoteConfigFallbackValue
import com.squareup.moshi.JsonReader
import okio.Buffer
import okio.ByteString.Companion.decodeBase64
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Collections

internal const val BUNDLED_REMOTE_CONFIG_DEFAULTS_FILE_NAME = "qonversion_remote_config_defaults.json"
internal const val BUNDLED_REMOTE_CONFIG_DEFAULTS_MAX_BYTES = 8 * 1024 * 1024
internal const val BUNDLED_REMOTE_CONFIG_DEFAULT_VALUE_MAX_BYTES = 64 * 1024
internal const val BUNDLED_REMOTE_CONFIG_DEFAULT_JSON_MAX_DEPTH = 64

private const val BUNDLED_REMOTE_CONFIG_DEFAULTS_SCHEMA_VERSION = 1
private const val BUNDLED_REMOTE_CONFIG_READ_BUFFER_BYTES = 8 * 1024
private const val BUNDLED_REMOTE_CONFIG_DEFAULTS_MAX_KEYS = 1_000
private const val BUNDLED_REMOTE_CONFIG_LOGICAL_KEY_MAX_BYTES = 256
private const val BUNDLED_REMOTE_CONFIG_UID_MAX_CODE_POINTS = 36
private const val BUNDLED_REMOTE_CONFIG_DEFAULTS_DIGEST_DOMAIN =
    "qonversion.remote-config-fallback-defaults.v1"
private const val PORTABLE_JSON_MAX_INTEGER = 9_007_199_254_740_991L
private val PORTABLE_JSON_MAX_INTEGER_BIG = BigInteger.valueOf(PORTABLE_JSON_MAX_INTEGER)
private val PORTABLE_JSON_MIN_INTEGER_BIG = PORTABLE_JSON_MAX_INTEGER_BIG.negate()
private val LOWERCASE_SHA256_PATTERN = Regex("^[0-9a-f]{64}$")

internal fun interface BundledRemoteConfigDefaultsAssetSource {
    fun open(context: Context): InputStream
}

internal class BundledRemoteConfigDefaultsReader(
    private val assetSource: BundledRemoteConfigDefaultsAssetSource =
        BundledRemoteConfigDefaultsAssetSource { context ->
            context.assets.open(BUNDLED_REMOTE_CONFIG_DEFAULTS_FILE_NAME)
        },
    private val maxArtifactBytes: Int = BUNDLED_REMOTE_CONFIG_DEFAULTS_MAX_BYTES,
) {
    fun read(context: Context): BundledRemoteConfigDefaultsDocument? {
        return try {
            val bytes = assetSource.open(context).use { it.readBounded(maxArtifactBytes) } ?: return null
            parse(bytes)
        } catch (_: Exception) {
            null
        }
    }

    @Suppress("ComplexMethod", "ComplexCondition", "ReturnCount")
    private fun parse(bytes: ByteArray): BundledRemoteConfigDefaultsDocument? {
        val source = bytes.decodeStrictUtf8() ?: return null
        val reader = JsonReader.of(Buffer().write(bytes))
        reader.isLenient = false

        return try {
            reader.beginObject()
            if (!reader.readExpectedName("schemaVersion")) return null
            val schemaVersion = reader.nextInt()
            if (!reader.readExpectedName("projectId")) return null
            val projectId = reader.nextLong()
            if (!reader.readExpectedName("environmentUid")) return null
            val environmentUid = reader.nextString()
            if (!reader.readExpectedName("releaseUid")) return null
            val releaseUid = reader.nextString()
            if (!reader.readExpectedName("releaseNumber")) return null
            val releaseNumber = reader.nextLong()
            if (!reader.readExpectedName("manifestContentHash")) return null
            val manifestContentHash = reader.nextString()
            if (!reader.readExpectedName("defaultsDigest")) return null
            val defaultsDigest = reader.nextString()
            if (!reader.readExpectedName("defaults")) return null
            val defaults = readDefaults(reader) ?: return null
            if (reader.hasNext()) return null
            reader.endObject()
            if (reader.peek() != JsonReader.Token.END_DOCUMENT) return null

            if (schemaVersion != BUNDLED_REMOTE_CONFIG_DEFAULTS_SCHEMA_VERSION ||
                projectId <= 0 || projectId > PORTABLE_JSON_MAX_INTEGER ||
                releaseNumber <= 0 || releaseNumber > PORTABLE_JSON_MAX_INTEGER ||
                !environmentUid.isValidRemoteConfigUid() || !releaseUid.isValidRemoteConfigUid() ||
                !LOWERCASE_SHA256_PATTERN.matches(manifestContentHash) ||
                !LOWERCASE_SHA256_PATTERN.matches(defaultsDigest)
            ) {
                return null
            }

            val computedDigest = computeDefaultsDigest(
                schemaVersion = schemaVersion,
                projectId = projectId,
                environmentUid = environmentUid,
                releaseUid = releaseUid,
                releaseNumber = releaseNumber,
                manifestContentHash = manifestContentHash,
                defaults = defaults,
            )
            if (computedDigest != defaultsDigest) return null

            val document = BundledRemoteConfigDefaultsDocument(
                projectId = projectId,
                environmentUid = environmentUid,
                releaseUid = releaseUid,
                releaseNumber = releaseNumber,
                manifestContentHash = manifestContentHash,
                defaultsDigest = defaultsDigest,
                defaults = defaults,
            )
            if (document.canonicalJson() != source) return null
            document
        } catch (_: Exception) {
            null
        }
    }

    @Suppress("ComplexMethod", "ComplexCondition", "ReturnCount")
    private fun readDefaults(reader: JsonReader): List<BundledRemoteConfigDefault>? {
        val defaults = mutableListOf<BundledRemoteConfigDefault>()
        reader.beginArray()
        var previousKeyBytes: ByteArray? = null
        while (reader.hasNext()) {
            if (defaults.size == BUNDLED_REMOTE_CONFIG_DEFAULTS_MAX_KEYS) return null
            reader.beginObject()
            if (!reader.readExpectedName("key")) return null
            val key = reader.nextString()
            if (!reader.readExpectedName("variationUid")) return null
            val variationUid = reader.nextString()
            if (!reader.readExpectedName("valueBase64")) return null
            val valueBase64 = reader.nextString()
            if (reader.hasNext()) return null
            reader.endObject()

            val keyBytes = key.toByteArray(StandardCharsets.UTF_8)
            if (keyBytes.isEmpty() || keyBytes.size > BUNDLED_REMOTE_CONFIG_LOGICAL_KEY_MAX_BYTES ||
                !variationUid.isValidRemoteConfigUid() ||
                previousKeyBytes?.let { compareUnsignedUtf8(it, keyBytes) >= 0 } == true
            ) {
                return null
            }
            previousKeyBytes = keyBytes

            val decoded = valueBase64.decodeBase64() ?: return null
            if (decoded.base64() != valueBase64) return null
            val rawJson = decoded.toByteArray()
            if (rawJson.isEmpty() || rawJson.size > BUNDLED_REMOTE_CONFIG_DEFAULT_VALUE_MAX_BYTES ||
                rawJson.decodeStrictUtf8() == null
            ) {
                return null
            }
            val parsedValue = parsePortableJson(rawJson) ?: return null
            defaults += BundledRemoteConfigDefault(
                key = key,
                variationUid = variationUid,
                valueBase64 = valueBase64,
                rawJson = rawJson,
                parsedValue = parsedValue.value,
            )
        }
        reader.endArray()
        return defaults
    }
}

internal class BundledRemoteConfigDefaultsCache(
    private val reader: BundledRemoteConfigDefaultsReader,
) {
    @Volatile
    private var loaded: LoadedDocument? = null

    fun value(context: Context, contextKey: String): QRemoteConfigFallbackValue? {
        val document = loadedDocument(context) ?: return null
        return document.defaultFor(contextKey)?.toPublicValue()
    }

    private fun loadedDocument(context: Context): BundledRemoteConfigDefaultsDocument? {
        val existing = loaded
        if (existing != null) return existing.document
        return synchronized(this) {
            val rechecked = loaded
            if (rechecked != null) {
                rechecked.document
            } else {
                reader.read(context).also { loaded = LoadedDocument(it) }
            }
        }
    }

    private data class LoadedDocument(val document: BundledRemoteConfigDefaultsDocument?)
}

internal object BundledRemoteConfigDefaults {
    @Volatile
    private var cache = defaultCache()

    fun value(context: Context, contextKey: String): QRemoteConfigFallbackValue? =
        cache.value(context, contextKey)

    @Synchronized
    internal fun installReaderForTests(reader: BundledRemoteConfigDefaultsReader) {
        cache = BundledRemoteConfigDefaultsCache(reader)
    }

    @Synchronized
    internal fun resetForTests() {
        cache = defaultCache()
    }

    private fun defaultCache() = BundledRemoteConfigDefaultsCache(BundledRemoteConfigDefaultsReader())
}

internal class BundledRemoteConfigDefaultsDocument(
    val projectId: Long,
    val environmentUid: String,
    val releaseUid: String,
    val releaseNumber: Long,
    val manifestContentHash: String,
    val defaultsDigest: String,
    defaults: List<BundledRemoteConfigDefault>,
) {
    private val orderedDefaults = Collections.unmodifiableList(defaults.toList())
    private val defaultsByKey = Collections.unmodifiableMap(orderedDefaults.associateBy { it.key })

    fun defaultFor(key: String): BundledRemoteConfigDefault? = defaultsByKey[key]
    internal fun allDefaults(): List<BundledRemoteConfigDefault> = orderedDefaults

    internal fun canonicalJson(): String = buildString {
        append("{\"schemaVersion\":1,\"projectId\":").append(projectId)
        append(",\"environmentUid\":").appendGoJsonString(environmentUid)
        append(",\"releaseUid\":").appendGoJsonString(releaseUid)
        append(",\"releaseNumber\":").append(releaseNumber)
        append(",\"manifestContentHash\":\"").append(manifestContentHash).append('"')
        append(",\"defaultsDigest\":\"").append(defaultsDigest).append('"')
        append(",\"defaults\":[")
        orderedDefaults.forEachIndexed { index, value ->
            if (index > 0) append(',')
            append("{\"key\":").appendGoJsonString(value.key)
            append(",\"variationUid\":").appendGoJsonString(value.variationUid)
            append(",\"valueBase64\":\"").append(value.valueBase64).append("\"}")
        }
        append("]}")
    }
}

internal class BundledRemoteConfigDefault(
    val key: String,
    val variationUid: String,
    val valueBase64: String,
    rawJson: ByteArray,
    private val parsedValue: Any?,
) {
    private val storedRawJson = rawJson.clone()
    val rawJsonBytes: ByteArray get() = storedRawJson.clone()

    fun toPublicValue() = QRemoteConfigFallbackValue(parsedValue)

    internal fun digestBytes(): ByteArray = storedRawJson.clone()
}

private data class ParsedJson(val value: Any?)

private fun parsePortableJson(bytes: ByteArray): ParsedJson? = try {
    val reader = JsonReader.of(Buffer().write(bytes))
    reader.isLenient = false
    val value = reader.readPortableJsonValue(depth = 1)
    if (reader.peek() != JsonReader.Token.END_DOCUMENT) null else ParsedJson(value)
} catch (_: Exception) {
    null
}

internal fun isPortableRemoteConfigJson(bytes: ByteArray): Boolean = parsePortableJson(bytes) != null

/**
 * Holds one decoded portable JSON value.
 *
 * The wrapper exists so a valid JSON `null` stays distinguishable from "these bytes are not
 * portable JSON": both would otherwise be a bare `null`, and the snapshot resolution ladder reads
 * a `null` decode as "reject this value and try the next ladder position".
 */
internal class PortableRemoteConfigJson(val value: Any?)

internal fun decodePortableRemoteConfigJson(bytes: ByteArray): PortableRemoteConfigJson? =
    parsePortableJson(bytes)?.let { parsed -> PortableRemoteConfigJson(parsed.value) }

@Suppress("ComplexMethod")
private fun JsonReader.readPortableJsonValue(depth: Int): Any? = when (peek()) {
    JsonReader.Token.BEGIN_ARRAY -> {
        if (depth > BUNDLED_REMOTE_CONFIG_DEFAULT_JSON_MAX_DEPTH) error("JSON is too deep")
        beginArray()
        val result = mutableListOf<Any?>()
        while (hasNext()) result += readPortableJsonValue(depth + 1)
        endArray()
        Collections.unmodifiableList(result)
    }
    JsonReader.Token.BEGIN_OBJECT -> {
        if (depth > BUNDLED_REMOTE_CONFIG_DEFAULT_JSON_MAX_DEPTH) error("JSON is too deep")
        beginObject()
        val result = linkedMapOf<String, Any?>()
        while (hasNext()) {
            val name = nextName()
            if (!name.hasValidSurrogatePairs()) error("unpaired surrogate in JSON object member")
            if (result.containsKey(name)) error("duplicate JSON object member")
            result[name] = readPortableJsonValue(depth + 1)
        }
        endObject()
        Collections.unmodifiableMap(result)
    }
    JsonReader.Token.STRING -> nextString().also {
        if (!it.hasValidSurrogatePairs()) error("unpaired surrogate in JSON string")
    }
    JsonReader.Token.NUMBER -> {
        val token = nextString()
        if (token.isPlainJsonInteger()) {
            val integer = BigInteger(token)
            if (integer < PORTABLE_JSON_MIN_INTEGER_BIG || integer > PORTABLE_JSON_MAX_INTEGER_BIG) {
                error("JSON integer is outside the portable range")
            }
        }
        token.toDouble().takeIf(Double::isFinite) ?: error("JSON number is not finite binary64")
    }
    JsonReader.Token.BOOLEAN -> nextBoolean()
    JsonReader.Token.NULL -> nextNull<Any?>()
    else -> error("expected one JSON value")
}

private fun String.isPlainJsonInteger(): Boolean = none { it == '.' || it == 'e' || it == 'E' }

private fun String.hasValidSurrogatePairs(): Boolean {
    var index = 0
    var valid = true
    while (index < length && valid) {
        val current = this[index]
        when {
            current.isHighSurrogate() -> {
                valid = index + 1 < length && this[index + 1].isLowSurrogate()
                if (valid) index += 2
            }
            current.isLowSurrogate() -> valid = false
            else -> index += 1
        }
    }
    return valid
}

private fun computeDefaultsDigest(
    schemaVersion: Int,
    projectId: Long,
    environmentUid: String,
    releaseUid: String,
    releaseNumber: Long,
    manifestContentHash: String,
    defaults: List<BundledRemoteConfigDefault>,
): String {
    val digest = MessageDigest.getInstance("SHA-256")
    digest.writeLengthPrefixed(BUNDLED_REMOTE_CONFIG_DEFAULTS_DIGEST_DOMAIN.toByteArray())
    digest.writeLengthPrefixed(schemaVersion.toString().toByteArray())
    digest.writeLengthPrefixed(projectId.toString().toByteArray())
    digest.writeLengthPrefixed(environmentUid.toByteArray())
    digest.writeLengthPrefixed(releaseUid.toByteArray())
    digest.writeLengthPrefixed(releaseNumber.toString().toByteArray())
    digest.writeLengthPrefixed(manifestContentHash.toByteArray())
    digest.writeLengthPrefixed(defaults.size.toString().toByteArray())
    defaults.forEach { value ->
        digest.writeLengthPrefixed(value.key.toByteArray())
        digest.writeLengthPrefixed(value.variationUid.toByteArray())
        digest.writeLengthPrefixed(value.digestBytes())
    }
    return digest.digest().toLowercaseHex()
}

private fun MessageDigest.writeLengthPrefixed(value: ByteArray) {
    update(ByteBuffer.allocate(Long.SIZE_BYTES).putLong(value.size.toLong()).array())
    update(value)
}

private fun ByteArray.toLowercaseHex(): String = buildString(size * 2) {
    for (byte in this@toLowercaseHex) {
        val value = byte.toInt() and BYTE_MASK
        append(HEX[value ushr HEX_HIGH_NIBBLE_SHIFT])
        append(HEX[value and HEX_NIBBLE_MASK])
    }
}

private fun JsonReader.readExpectedName(expected: String): Boolean =
    hasNext() && nextName() == expected

private fun String.isValidRemoteConfigUid(): Boolean =
    isNotEmpty() && codePointCount(0, length) <= BUNDLED_REMOTE_CONFIG_UID_MAX_CODE_POINTS

private fun compareUnsignedUtf8(left: ByteArray, right: ByteArray): Int {
    val sharedLength = minOf(left.size, right.size)
    for (index in 0 until sharedLength) {
        val comparison = (left[index].toInt() and BYTE_MASK)
            .compareTo(right[index].toInt() and BYTE_MASK)
        if (comparison != 0) return comparison
    }
    return left.size.compareTo(right.size)
}

private fun InputStream.readBounded(maxBytes: Int): ByteArray? {
    val output = ByteArrayOutputStream(minOf(maxBytes, BUNDLED_REMOTE_CONFIG_READ_BUFFER_BYTES))
    val buffer = ByteArray(BUNDLED_REMOTE_CONFIG_READ_BUFFER_BYTES)
    var total = 0
    var read = read(buffer)
    while (read >= 0) {
        if (read > 0) {
            if (read > maxBytes - total) return null
            output.write(buffer, 0, read)
            total += read
        }
        read = read(buffer)
    }
    return output.toByteArray()
}

private fun ByteArray.decodeStrictUtf8(): String? = try {
    StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(this))
        .toString()
} catch (_: Exception) {
    null
}

@Suppress("ComplexMethod")
private fun StringBuilder.appendGoJsonString(value: String): StringBuilder {
    append('"')
    value.forEach { character ->
        when (character) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\b' -> append("\\b")
            '\u000c' -> append("\\f")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            '<' -> append("\\u003c")
            '>' -> append("\\u003e")
            '&' -> append("\\u0026")
            '\u2028' -> append("\\u2028")
            '\u2029' -> append("\\u2029")
            else -> if (character < ' ') {
                append("\\u00")
                append(HEX[(character.code ushr HEX_HIGH_NIBBLE_SHIFT) and HEX_NIBBLE_MASK])
                append(HEX[character.code and HEX_NIBBLE_MASK])
            } else {
                append(character)
            }
        }
    }
    return append('"')
}

private const val BYTE_MASK = 0xff
private const val HEX_HIGH_NIBBLE_SHIFT = 4
private const val HEX_NIBBLE_MASK = 0x0f
private const val HEX = "0123456789abcdef"
