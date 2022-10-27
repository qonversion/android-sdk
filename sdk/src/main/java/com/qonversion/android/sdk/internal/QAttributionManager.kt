package com.qonversion.android.sdk.internal

import com.qonversion.android.sdk.dto.QAttributionSource
import com.qonversion.android.sdk.internal.provider.AppStateProvider

internal class QAttributionManager internal constructor(
    private val repository: QonversionRepository,
    private val appStateProvider: AppStateProvider
) {
    private var pendingAttributionSource: QAttributionSource? = null
    private var pendingConversionInfo: Map<String, Any>? = null

    fun onAppForeground() {
        val source = pendingAttributionSource
        val info = pendingConversionInfo
        if (source != null && !info.isNullOrEmpty()) {
            repository.attribution(info, source.id)

            pendingConversionInfo = null
            pendingAttributionSource = null
        }
    }

    fun attribution(
        conversionInfo: Map<String, Any>,
        from: QAttributionSource
    ) {
        if (appStateProvider.appState.isBackground()) {
            pendingAttributionSource = from
            pendingConversionInfo = conversionInfo
            return
        }

        repository.attribution(conversionInfo, from.id)
    }
}
