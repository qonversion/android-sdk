package com.qonversion.android.sdk.dto.remoteconfig

import com.qonversion.android.sdk.ExperimentalQonversionApi

/**
 * The completion value of a fetch.
 *
 * [snapshot] is the best available data at completion time: the last fetched release when one is
 * held (which, on a successful fetch, is the release this call just brought in), otherwise the
 * currently activated release, otherwise the bundled defaults. Each key read from it still reports
 * its own [QRemoteConfigSource], including on a [QRemoteConfigFetchStatus.TimedOut] completion.
 *
 * It is therefore a *fetch* view, not the activated one: it can show a release that
 * `QRemoteConfigSnapshots.current` will only serve after the next `activate()`.
 */
@ExperimentalQonversionApi
class QRemoteConfigFetchResult internal constructor(
    val status: QRemoteConfigFetchStatus,
    val snapshot: QRemoteConfigSnapshot,
)
