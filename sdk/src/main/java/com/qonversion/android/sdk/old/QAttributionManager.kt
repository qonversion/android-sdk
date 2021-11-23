package com.qonversion.android.sdk.old

class QAttributionManager internal constructor(
    private val repository: QonversionRepository
) {
    private var pendingAttributionSource: AttributionSource? = null
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
        from: AttributionSource
    ) {
        if (Qonversion.appState.isBackground()) {
            pendingAttributionSource = from
            pendingConversionInfo = conversionInfo
            return
        }

        repository.attribution(conversionInfo, from.id)
    }
}
