package com.qonversion.android.sdk.internal

internal data class QonversionConfig(
    val key: String,
    val sdkVersion: String,
    val isDebugMode: Boolean,
    val isObserveMode: Boolean
) {
    @Volatile
    var fatalError: HttpError? = null
        @Synchronized set
        @Synchronized get

    @Volatile
    var uid = ""
        @Synchronized set
        @Synchronized get
}
