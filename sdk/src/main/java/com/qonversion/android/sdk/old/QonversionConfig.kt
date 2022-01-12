package com.qonversion.android.sdk.old

data class QonversionConfig(
    val key: String,
    val isDebugMode: Boolean
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
