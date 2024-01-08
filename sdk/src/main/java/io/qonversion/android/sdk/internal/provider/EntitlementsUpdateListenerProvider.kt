package io.qonversion.android.sdk.internal.provider

import io.qonversion.android.sdk.listeners.QEntitlementsUpdateListener

internal interface EntitlementsUpdateListenerProvider {

    val entitlementsUpdateListener: QEntitlementsUpdateListener?
}
