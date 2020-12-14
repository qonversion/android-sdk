package com.qonversion.android.sdk

class QAttributionManager internal constructor(
    private val repository: QonversionRepository
){
    fun attribution(
        conversionInfo: Map<String, Any>,
        from: AttributionSource
    ) {
        repository.attribution(conversionInfo, from.id)
    }
}