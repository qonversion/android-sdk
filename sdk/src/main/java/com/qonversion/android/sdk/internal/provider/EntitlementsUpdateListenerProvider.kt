package com.qonversion.android.sdk.internal.provider

import com.qonversion.android.sdk.listeners.EntitlementsUpdateListener

internal interface EntitlementsUpdateListenerProvider {

    val entitlementsUpdateListener: EntitlementsUpdateListener?
}
