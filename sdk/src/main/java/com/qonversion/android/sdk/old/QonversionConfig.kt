package com.qonversion.android.sdk.old

import com.qonversion.android.sdk.old.HttpError

data class QonversionConfig(
    val key: String,
    val sdkVersion: String,
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
