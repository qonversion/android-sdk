package io.qonversion.nocodes.internal.provider

import io.qonversion.nocodes.interfaces.PurchaseHandlerDelegate

internal interface PurchaseHandlerDelegateProvider {

    var purchaseHandlerDelegate: PurchaseHandlerDelegate?
}
