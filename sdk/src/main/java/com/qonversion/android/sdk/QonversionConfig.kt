package com.qonversion.android.sdk

internal data class QonversionConfig(
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
        @Synchronized private set
        @Synchronized get

    private var userChangedListeners: Set<QUserChangedListener> = emptySet()

    fun setUid(uid: String) {
        if (uid == this.uid) return

        val oldUid = this.uid
        this.uid = uid

        userChangedListeners.forEach { it.onUserChanged(oldUid, uid) }
    }

    fun subscribeOnUserChanges(listener: QUserChangedListener) {
        userChangedListeners.plus(listener)
    }
}
