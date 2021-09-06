package com.qonversion.android.sdk

data class QonversionConfig(
    val key: String,
    val sdkVersion: String,
    val isDebugMode: Boolean
) {
    @Volatile
    var fatalError: HttpError? = null
        @Synchronized set
        @Synchronized get
}
