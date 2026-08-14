package com.qonversion.android.sdk.internal.remoteconfig

import com.qonversion.android.sdk.internal.services.BundledRemoteConfigDefault
import com.qonversion.android.sdk.internal.services.BundledRemoteConfigDefaultsDocument
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Assert.assertThrows

internal class RemoteConfigSnapshotTest {
    @Test
    fun `typed resolution uses previous only when current raw cannot decode`() {
        val current = release(
            uid = "release-2",
            number = 2,
            values = mapOf(
                "paywall" to "\"not-an-object\"",
                "title" to "\"server-title\"",
            ),
        )
        val previous = release(
            uid = "release-1",
            number = 1,
            values = mapOf(
                "paywall" to "{\"title\":\"cached\"}",
                "title" to "\"cached-title\"",
                "previous-only" to "\"must-not-leak\"",
            ),
        )
        val bundled = release(
            uid = "bundle",
            number = 1,
            values = mapOf(
                "paywall" to "{\"title\":\"fallback\"}",
                "bundle-only" to "true",
            ),
        )
        val snapshot = RemoteConfigSnapshot(current, previous, bundled)

        val typed = snapshot.value("paywall") { raw ->
            raw.decodeToString().takeIf { it.startsWith("{") }
        }
        val raw = snapshot.rawValue("paywall")

        assertEquals(RemoteConfigSnapshotValueSource.Cache, typed?.source)
        assertEquals("{\"title\":\"cached\"}", typed?.value)
        assertEquals(RemoteConfigSnapshotValueSource.Server, raw?.source)
        assertArrayEquals("\"not-an-object\"".encodeToByteArray(), raw?.value)
        assertNull(snapshot.rawValue("previous-only"))
        assertEquals(RemoteConfigSnapshotValueSource.Fallback, snapshot.rawValue("bundle-only")?.source)
    }

    @Test
    fun `missing and tombstone skip previous and resolve directly to bundle`() {
        val current = RemoteConfigSnapshotRelease(
            releaseUid = "release-2",
            releaseNumber = 2,
            manifestContentHash = "a".repeat(64),
            entries = listOf(RemoteConfigSnapshotEntry.tombstone("removed")),
        )
        val previous = release(
            uid = "release-1",
            number = 1,
            values = mapOf("removed" to "\"private-old\"", "missing" to "\"private-old\""),
        )
        val bundled = release(
            uid = "bundle",
            number = 1,
            values = mapOf("removed" to "\"safe-default\"", "missing" to "\"safe-default\""),
        )
        val snapshot = RemoteConfigSnapshot(current, previous, bundled)

        for (key in listOf("removed", "missing")) {
            val raw = snapshot.rawValue(key)
            val typed = snapshot.value(key) { it.decodeToString() }

            assertEquals(RemoteConfigSnapshotValueSource.Fallback, raw?.source)
            assertArrayEquals("\"safe-default\"".encodeToByteArray(), raw?.value)
            assertEquals(RemoteConfigSnapshotValueSource.Fallback, typed?.source)
            assertEquals("\"safe-default\"", typed?.value)
        }
    }

    @Test
    fun `held snapshot values metadata and updates are deeply immutable`() {
        val raw = "{\"enabled\":true}".encodeToByteArray()
        val metadata = "{\"reset\":true}".encodeToByteArray()
        val entry = RemoteConfigSnapshotEntry.value(
            key = "feature",
            rawValue = raw,
            variationUid = "variation",
            applyPolicy = RemoteConfigSnapshotApplyPolicy.OnNextActivate,
            metadata = metadata,
        )
        val snapshot = RemoteConfigSnapshot(
            primaryRelease = RemoteConfigSnapshotRelease("release", 1, "a".repeat(64), listOf(entry)),
            previousRelease = null,
            bundledRelease = null,
        )
        val update = RemoteConfigSnapshotUpdate(
            snapshot = snapshot,
            changedKeys = setOf("feature"),
            metadataByKey = mapOf("feature" to metadata),
        )

        raw.fill('x'.code.toByte())
        metadata.fill('x'.code.toByte())
        snapshot.rawValue("feature")?.value?.fill('y'.code.toByte())
        update.metadataForKey("feature")?.fill('y'.code.toByte())

        assertArrayEquals("{\"enabled\":true}".encodeToByteArray(), snapshot.rawValue("feature")?.value)
        assertArrayEquals("{\"reset\":true}".encodeToByteArray(), snapshot.metadataForKey("feature"))
        assertArrayEquals("{\"reset\":true}".encodeToByteArray(), update.metadataForKey("feature"))
        assertEquals(setOf("feature"), update.changedKeys)
    }

    @Test
    fun `bundled document integrates exact raw defaults without mutable aliases`() {
        val raw = "{\"enabled\":true}".encodeToByteArray()
        val document = BundledRemoteConfigDefaultsDocument(
            projectId = 42,
            environmentUid = "env-production",
            releaseUid = "bundle-release",
            releaseNumber = 7,
            manifestContentHash = "a".repeat(64),
            defaultsDigest = "b".repeat(64),
            defaults = listOf(
                BundledRemoteConfigDefault(
                    key = "feature",
                    variationUid = "bundle-variation",
                    valueBase64 = "unused-by-adapter",
                    rawJson = raw,
                    parsedValue = emptyMap<String, Any>(),
                ),
            ),
        )

        val scopedRelease = document.toScopedRemoteConfigSnapshotRelease("sdk-project-key")
        val release = scopedRelease.release
        raw.fill('x'.code.toByte())

        assertEquals("sdk-project-key", scopedRelease.projectKey)
        assertEquals("env-production", scopedRelease.environment)
        assertEquals("bundle-release", release.releaseUid)
        assertEquals(7, release.releaseNumber)
        assertTrue(release.entries.keys.contains("feature"))
        assertArrayEquals("{\"enabled\":true}".encodeToByteArray(), release.entry("feature")?.rawValueBytes)
    }

    @Test
    fun `release and entries enforce exact server resource and identifier bounds`() {
        assertThrows(IllegalArgumentException::class.java) {
            RemoteConfigSnapshotEntry.value(
                key = "key",
                rawValue = ("\"" + "x".repeat(64 * 1024) + "\"").encodeToByteArray(),
                variationUid = "variation",
                applyPolicy = RemoteConfigSnapshotApplyPolicy.OnNextActivate,
                metadata = null,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            RemoteConfigSnapshotEntry.value(
                key = "key",
                rawValue = "true".encodeToByteArray(),
                variationUid = "variation",
                applyPolicy = RemoteConfigSnapshotApplyPolicy.OnNextActivate,
                metadata = ("\"" + "x".repeat(4 * 1024) + "\"").encodeToByteArray(),
            )
        }
        val entry = RemoteConfigSnapshotEntry.value(
            key = "key",
            rawValue = "true".encodeToByteArray(),
            variationUid = "variation",
            applyPolicy = RemoteConfigSnapshotApplyPolicy.OnNextActivate,
            metadata = null,
        )
        for (invalidHash in listOf("hash", "A".repeat(64), "a".repeat(63))) {
            assertThrows(IllegalArgumentException::class.java) {
                RemoteConfigSnapshotRelease("release", 1, invalidHash, listOf(entry))
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            RemoteConfigSnapshotRelease("r".repeat(37), 1, "a".repeat(64), listOf(entry))
        }
        assertThrows(IllegalArgumentException::class.java) {
            RemoteConfigSnapshotRelease(
                "release",
                1,
                "a".repeat(64),
                List(1_001) { index -> RemoteConfigSnapshotEntry.tombstone("key-$index") },
            )
        }

        val nearMaximumRaw = ("\"" + "x".repeat(64 * 1024 - 2) + "\"").encodeToByteArray()
        assertThrows(IllegalArgumentException::class.java) {
            RemoteConfigSnapshotRelease(
                "release",
                1,
                "a".repeat(64),
                List(65) { index ->
                    RemoteConfigSnapshotEntry.value(
                        key = "key-$index",
                        rawValue = nearMaximumRaw,
                        variationUid = "variation-$index",
                        applyPolicy = RemoteConfigSnapshotApplyPolicy.OnNextActivate,
                        metadata = null,
                    )
                },
            )
        }
    }

    @Test
    fun `scope enforces exact identity boundary limits`() {
        val valid = RemoteConfigSnapshotScope(
            projectKey = "é".repeat(128),
            environment = "😀".repeat(36),
            canonicalUserId = "u".repeat(256),
        )

        assertEquals(256, valid.projectKey.toByteArray().size)
        assertEquals(36, valid.environment.codePointCount(0, valid.environment.length))
        assertEquals(256, valid.canonicalUserId.toByteArray().size)

        for (invalid in listOf(
            Triple("", "production", "user"),
            Triple("p".repeat(257), "production", "user"),
            Triple("project", "", "user"),
            Triple("project", "e".repeat(37), "user"),
            Triple("project", "production", "u".repeat(257)),
            Triple("project", "production", "\uD800"),
        )) {
            assertThrows(IllegalArgumentException::class.java) {
                RemoteConfigSnapshotScope(invalid.first, invalid.second, invalid.third)
            }
        }
    }

    @Test
    fun `scope storage identity remains unambiguous when values contain separators`() {
        val first = RemoteConfigSnapshotScope("a\u0000b", "c", "d")
        val second = RemoteConfigSnapshotScope("a", "b", "c\u0000d")

        assertTrue(
            com.qonversion.android.sdk.internal.storage.remoteConfigSnapshotStorageKey(first) !=
                com.qonversion.android.sdk.internal.storage.remoteConfigSnapshotStorageKey(second),
        )
    }

    private fun release(
        uid: String,
        number: Long,
        values: Map<String, String>,
        policy: RemoteConfigSnapshotApplyPolicy = RemoteConfigSnapshotApplyPolicy.OnNextActivate,
    ) = RemoteConfigSnapshotRelease(
        releaseUid = uid,
        releaseNumber = number,
        manifestContentHash = "a".repeat(64),
        entries = values.map { (key, value) ->
            RemoteConfigSnapshotEntry.value(
                key = key,
                rawValue = value.encodeToByteArray(),
                variationUid = "$uid-$key",
                applyPolicy = policy,
                metadata = null,
            )
        },
    )
}
