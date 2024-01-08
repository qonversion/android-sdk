package io.qonversion.android.sdk.internal

import io.qonversion.android.sdk.dto.QAttributionProvider
import io.qonversion.android.sdk.internal.provider.AppStateProvider
import io.qonversion.android.sdk.internal.repository.QRepository

internal class QAttributionManager internal constructor(
    private val repository: QRepository,
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
