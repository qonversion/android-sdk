package com.qonversion.android.sdk.internal.di.controllers

import com.qonversion.android.sdk.internal.billing.PurchasesListener
import com.qonversion.android.sdk.internal.billing.controller.GoogleBillingController
import com.qonversion.android.sdk.internal.userProperties.controller.UserPropertiesController

internal interface ControllersAssembly {

    fun userPropertiesController(): UserPropertiesController

    fun googleBillingController(purchasesListener: PurchasesListener): GoogleBillingController
}
