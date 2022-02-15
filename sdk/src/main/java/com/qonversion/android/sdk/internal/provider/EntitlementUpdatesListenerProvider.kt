package com.qonversion.android.sdk.internal.provider

import com.qonversion.android.sdk.listeners.EntitlementUpdatesListener

internal interface EntitlementUpdatesListenerProvider {

    val entitlementUpdatesListener: EntitlementUpdatesListener?
}
