package com.qonversion.android.sdk.dto

data class QRemoteConfigList internal constructor(
    internal val remoteConfigs: List<QRemoteConfig>
) {
    fun remoteConfigForKey(key: String): QRemoteConfig? {
        return remoteConfigs.find { it.source.contextKey == key }
    }

    val remoteConfigForEmptyKey: QRemoteConfig? = remoteConfigs.find { it.source.contextKey == null }
}
