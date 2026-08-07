package com.qonversion.android.sdk.internal.remoteconfig

/**
 * The two moments the v1 Remote Config pipeline owns and the v2 pipeline must observe.
 *
 * It exists as its own object rather than as fields on `QRemoteConfigManager` so the optional v2
 * subsystem adds one collaborator to that class instead of more surface, and so the "an optional
 * subsystem can never break the v1 transition" rule is written once, here.
 */
internal class RemoteConfigIdentityBridge {

    /**
     * The canonical uid changed (logout, or an identify that minted a new one). The v2 scope must
     * switch immediately, dropping the previous identity's release.
     */
    var onIdentityScopeChanged: (() -> Unit)? = null

    /**
     * The targeting inputs changed while the identity stayed the same — an identify that only
     * attached an external id, a user-property batch, an experiment attach/detach, or an explicit
     * cache invalidation. The v2 pipeline must re-read targeting but keep serving its release.
     */
    var onTargetingInvalidated: (() -> Unit)? = null

    fun identityScopeChanged() = notify(onIdentityScopeChanged)

    fun targetingInvalidated() = notify(onTargetingInvalidated)

    private fun notify(observer: (() -> Unit)?) {
        try {
            observer?.invoke()
        } catch (@Suppress("TooGenericExceptionCaught", "SwallowedException") _: RuntimeException) {
            // An optional subsystem can never break the v1 identity transition or invalidation.
        }
    }
}
