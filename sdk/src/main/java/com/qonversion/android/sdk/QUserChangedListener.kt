package com.qonversion.android.sdk

internal interface QUserChangedListener {

    fun onUserChanged(oldUid: String, newUid: String)
}
