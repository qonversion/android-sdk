package com.qonversion.android.sdk

class QAttributionManager internal constructor(
    private val repository: QonversionRepository
){
    private var isAppBackground: Boolean = true
    private var pendingAttributionSource: AttributionSource? = null
    private var pendingConversionInfo: Map<String, Any>? = null

    fun onAppForeground() {
        isAppBackground = false
        val source = pendingAttributionSource
        val info = pendingConversionInfo
        if (source != null && !info.isNullOrEmpty()) {
            repository.attribution(info, source.id)

            pendingConversionInfo = null
            pendingAttributionSource = null
        }
    }

    fun onAppBackground() {
        isAppBackground = true
    }

    fun attribution(
        conversionInfo: Map<String, Any>,
        from: AttributionSource
    ) {
        if (isAppBackground) {
            pendingAttributionSource = from
            pendingConversionInfo = conversionInfo
            return
        }

        repository.attribution(conversionInfo, from.id)
    }
}