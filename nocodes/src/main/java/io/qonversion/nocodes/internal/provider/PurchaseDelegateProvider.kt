package io.qonversion.nocodes.internal.provider

import io.qonversion.nocodes.interfaces.PurchaseDelegate

internal interface PurchaseDelegateProvider {

    var purchaseDelegate: PurchaseDelegate?
}
