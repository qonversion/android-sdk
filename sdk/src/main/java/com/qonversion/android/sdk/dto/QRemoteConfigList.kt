package com.qonversion.android.sdk.dto

data class QRemoteConfigList internal constructor(
    internal val remoteConfigs: List<QRemoteConfig>
) {
    fun remoteConfigForContextKey(key: String): QRemoteConfig? {
        return remoteConfigs.find { it.source.contextKey == key }
    }

    val remoteConfigForEmptyContextKey: QRemoteConfig? = remoteConfigs.find {
        it.source.contextKey == null
    }
}
