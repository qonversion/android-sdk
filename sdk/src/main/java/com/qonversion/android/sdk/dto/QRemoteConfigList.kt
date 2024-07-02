package com.qonversion.android.sdk.dto
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class QRemoteConfigList internal constructor(
    val remoteConfigs: List<QRemoteConfig>
) {
    fun remoteConfigForContextKey(key: String): QRemoteConfig? {
        return remoteConfigs.find { it.source.contextKey == key }
    }

    val remoteConfigForEmptyContextKey: QRemoteConfig? = remoteConfigs.find {
        it.source.contextKey == null
    }
}
