package com.qonversion.android.sdk

class QAttributionManager internal constructor(
    private val repository: QonversionRepository
){
    fun attribution(
        conversionInfo: Map<String, Any>,
        from: AttributionSource,
        conversionUid: String
    ) {
        repository.attribution(conversionInfo, from.id, conversionUid)
    }
}