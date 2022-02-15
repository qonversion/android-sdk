package com.qonversion.android.sdk.internal.provider

import com.qonversion.android.sdk.listeners.EntitlementsListener

internal interface EntitlementsListenerProvider {

    val entitlementsListener: EntitlementsListener?
}
