package com.qonversion.android.sdk.internal.di.controllers

import com.qonversion.android.sdk.internal.billing.PurchasesListener
import com.qonversion.android.sdk.internal.billing.controller.GoogleBillingController
import com.qonversion.android.sdk.internal.billing.controller.GoogleBillingControllerImpl
import com.qonversion.android.sdk.internal.di.misc.MiscAssembly
import com.qonversion.android.sdk.internal.di.services.ServicesAssembly
import com.qonversion.android.sdk.internal.di.storage.StorageAssembly
import com.qonversion.android.sdk.internal.userProperties.controller.UserPropertiesController
import com.qonversion.android.sdk.internal.userProperties.controller.UserPropertiesControllerImpl

internal class ControllersAssemblyImpl(
    private val storageAssembly: StorageAssembly,
    private val miscAssembly: MiscAssembly,
    private val servicesAssembly: ServicesAssembly
) : ControllersAssembly {
    override val userPropertiesController: UserPropertiesController
        get() = UserPropertiesControllerImpl(
            pendingPropertiesStorage = storageAssembly.pendingUserPropertiesStorage,
            sentPropertiesStorage = storageAssembly.sentUserPropertiesStorage,
            service = servicesAssembly.userPropertiesService,
            worker = miscAssembly.delayedWorker,
            logger = miscAssembly.logger
        )

    override fun getGoogleBillingController(purchasesListener: PurchasesListener): GoogleBillingController {
        return GoogleBillingControllerImpl(
            miscAssembly.googleBillingConsumer,
            miscAssembly.googleBillingPurchaser,
            miscAssembly.googleBillingDataFetcher,
            purchasesListener,
            miscAssembly.logger
        )
    }
}
