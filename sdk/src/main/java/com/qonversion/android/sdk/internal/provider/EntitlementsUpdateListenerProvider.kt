package com.qonversion.android.sdk.internal.provider

import com.qonversion.android.sdk.listeners.QEntitlementsUpdateListener

internal interface EntitlementsUpdateListenerProvider {

    val entitlementsUpdateListener: QEntitlementsUpdateListener?
}
