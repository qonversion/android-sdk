@file:OptIn(ExperimentalQonversionApi::class)

package com.qonversion.android.sdk.internal.remoteconfig

import com.qonversion.android.sdk.ExperimentalQonversionApi
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections

private const val RELEASE_1 = 1L
private const val RELEASE_2 = 2L
private const val CACHED_VALUE = "cached"
private const val SERVED_VALUE = "served"

/**
 * The `decode_failure` production point, at the snapshot read site.
 *
 * This is the one telemetry kind with no other seam: a failed typed decode is absorbed by the
 * resolution ladder by design, so nothing downstream of the read can tell a mis-typed key from an
 * absent one. The tests therefore assert two things at once, and the first one is the important
 * one: **the value the caller receives is exactly what it would be without telemetry.**
 */
internal class RemoteConfigDecodeFailureTelemetryTest {
    private val reported: MutableList<Pair<String, Long>> = Collections.synchronizedList(mutableListOf())
    private val observer = RemoteConfigDecodeFailureObserver { key, release -> reported += key to release }
    private var harness: RemoteConfigV2Harness? = null

    @After
    fun tearDown() {
        harness?.shutdown()
    }

    @Test
    fun `a failed decode of the served release falls through to the cached one and is reported`() {
        val snapshot = snapshot(
            primary = release(RELEASE_2, "count" to "\"not-a-number\""),
            previous = release(RELEASE_1, "count" to "7"),
        )

        val resolved = snapshot.value("count") { bytes -> bytes.toString(Charsets.UTF_8).toLongOrNull() }

        // The ladder is unchanged: the cached release still serves the value.
        assertEquals(7L, resolved?.value)
        assertEquals(RemoteConfigSnapshotValueSource.Cache, resolved?.source)
        assertEquals(listOf("count" to RELEASE_2), reported)
    }

    @Test
    fun `a failed decode with nothing below it still returns null and is reported once`() {
        val snapshot = snapshot(primary = release(RELEASE_2, "count" to "\"not-a-number\""))

        val resolved = snapshot.value("count") { bytes -> bytes.toString(Charsets.UTF_8).toLongOrNull() }

        assertNull(resolved)
        assertEquals(listOf("count" to RELEASE_2), reported)
    }

    @Test
    fun `a decoder that throws is reported exactly like one that returns null`() {
        val snapshot = snapshot(primary = release(RELEASE_2, "count" to "1"))

        val resolved = snapshot.value<Long>("count") { error("the app's decoder blew up") }

        assertNull(resolved)
        assertEquals(listOf("count" to RELEASE_2), reported)
    }

    @Test
    fun `a successful decode reports nothing`() {
        val snapshot = snapshot(primary = release(RELEASE_2, "count" to "7"))

        val resolved = snapshot.value("count") { bytes -> bytes.toString(Charsets.UTF_8).toLongOrNull() }

        assertEquals(7L, resolved?.value)
        assertTrue(reported.isEmpty())
    }

    @Test
    fun `a key the served release does not carry is not a decode failure`() {
        val snapshot = snapshot(
            primary = release(RELEASE_2, "other" to "7"),
            bundled = release(RELEASE_1, "count" to "\"$CACHED_VALUE\""),
        )

        // Falling back because the key is absent is the ladder working, not a client defect.
        val resolved = snapshot.value("count") { bytes -> bytes.toString(Charsets.UTF_8).trim('"') }

        assertEquals(CACHED_VALUE, resolved?.value)
        assertTrue(reported.isEmpty())
    }

    @Test
    fun `an observer that throws cannot break a read`() {
        val snapshot = RemoteConfigSnapshot(
            primaryRelease = release(RELEASE_2, "count" to "\"not-a-number\""),
            previousRelease = release(RELEASE_1, "count" to "7"),
            bundledRelease = null,
            decodeFailureObserver = { _, _ -> error("telemetry blew up") },
        )

        val resolved = snapshot.value("count") { bytes -> bytes.toString(Charsets.UTF_8).toLongOrNull() }

        assertEquals(7L, resolved?.value)
    }

    @Test
    fun `repeated failures of the same key coalesce into one telemetry entry`() {
        // No bundled defaults: the fallback rung would otherwise decode the key and hide the
        // failure this test is about.
        val started = RemoteConfigV2Harness(bundled = null).also { harness = it }
        started.serve("release-1", RELEASE_1, listOf(RcWireValue("count", "\"$SERVED_VALUE\"")))
        started.identify("QON_anon_a", "canonical-a", RemoteConfigFetchForceReason.Build)
        started.fetchBlocking()
        started.activateBlocking()
        started.awaitWorkerIdle()

        // The app reads the same key with the wrong type, over and over.
        val snapshot = started.core.currentSnapshot()
        repeat(5) {
            assertNull(snapshot.value("count") { bytes -> bytes.toString(Charsets.UTF_8).toLongOrNull() })
        }

        started.awaitWorkerIdle()
        // Bounded: five failed reads are one buffered entry, not five.
        assertEquals(1, started.telemetrySender.pendingEntryCount)
        assertEquals(0, started.telemetrySender.droppedEventCount)
    }

    private fun snapshot(
        primary: RemoteConfigSnapshotRelease? = null,
        previous: RemoteConfigSnapshotRelease? = null,
        bundled: RemoteConfigSnapshotRelease? = null,
    ) = RemoteConfigSnapshot(primary, previous, bundled, observer)

    private fun release(releaseNumber: Long, vararg values: Pair<String, String>) = RemoteConfigSnapshotRelease(
        releaseUid = "release-$releaseNumber",
        releaseNumber = releaseNumber,
        manifestContentHash = "1".repeat(RC_FINGERPRINT_LENGTH),
        entries = values.map { (key, raw) ->
            RemoteConfigSnapshotEntry.value(
                key = key,
                rawValue = raw.encodeToByteArray(),
                variationUid = "var-$key-$releaseNumber",
                applyPolicy = RemoteConfigSnapshotApplyPolicy.OnNextActivate,
                metadata = null,
            )
        },
    )
}
