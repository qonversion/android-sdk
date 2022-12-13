package com.qonversion.android.sdk.internal

import com.qonversion.android.sdk.dto.QAttributionProvider
import com.qonversion.android.sdk.internal.provider.AppStateProvider

internal class QAttributionManager internal constructor(
    private val repository: QonversionRepository,
    private val appStateProvider: AppStateProvider
) {
    private var pendingAttributionProvider: QAttributionProvider? = null
    private var pendingData: Map<String, Any>? = null

    fun onAppForeground() {
        val source = pendingAttributionProvider
        val info = pendingData
        if (source != null && !info.isNullOrEmpty()) {
            repository.attribution(info, source.id)

            pendingData = null
            pendingAttributionProvider = null
        }
    }

    fun attribution(data: Map<String, Any>, provider: QAttributionProvider) {
        if (appStateProvider.appState.isBackground()) {
            pendingAttributionProvider = provider
            pendingData = data
            return
        }

        repository.attribution(data, provider.id)
    }
}
