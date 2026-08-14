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
    var onIdentityScopeChanged: ((externalUserId: String?) -> Unit)? = null

    /**
     * The targeting inputs changed while the identity stayed the same — an identify that only
     * attached an external id, a user-property batch, an experiment attach/detach, or an explicit
     * cache invalidation. The v2 pipeline must re-read targeting but keep serving its release.
     */
    var onTargetingInvalidated: ((externalUserId: String?) -> Unit)? = null

    fun identityScopeChanged(externalUserId: String? = null) = notifyIdentity(onIdentityScopeChanged, externalUserId)

    fun targetingInvalidated(externalUserId: String? = null) = notifyIdentity(onTargetingInvalidated, externalUserId)

    private fun notifyIdentity(observer: ((String?) -> Unit)?, externalUserId: String?) {
        try {
            observer?.invoke(externalUserId)
        } catch (@Suppress("TooGenericExceptionCaught", "SwallowedException") _: RuntimeException) {
            // An optional subsystem can never break the v1 identity transition or invalidation.
        }
    }
}
