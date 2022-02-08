package com.qonversion.android.sdk.internal.di.controllers

import com.qonversion.android.sdk.internal.billing.PurchasesListener
import com.qonversion.android.sdk.internal.billing.controller.GoogleBillingController
import com.qonversion.android.sdk.internal.di.misc.MiscAssembly
import com.qonversion.android.sdk.internal.di.services.ServicesAssembly
import com.qonversion.android.sdk.internal.userProperties.controller.UserPropertiesController

internal interface ControllersAssembly {

    fun init(miscAssembly: MiscAssembly, servicesAssembly: ServicesAssembly)

    val userPropertiesController: UserPropertiesController

    fun getGoogleBillingController(purchasesListener: PurchasesListener): GoogleBillingController
}
