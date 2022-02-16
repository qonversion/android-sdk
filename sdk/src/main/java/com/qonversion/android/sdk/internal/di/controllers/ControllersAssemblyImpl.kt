package com.qonversion.android.sdk.internal.di.controllers

import androidx.annotation.VisibleForTesting
import com.qonversion.android.sdk.internal.billing.PurchasesListener
import com.qonversion.android.sdk.internal.billing.consumer.GoogleBillingConsumer
import com.qonversion.android.sdk.internal.billing.consumer.GoogleBillingConsumerImpl
import com.qonversion.android.sdk.internal.billing.controller.GoogleBillingController
import com.qonversion.android.sdk.internal.billing.controller.GoogleBillingControllerImpl
import com.qonversion.android.sdk.internal.billing.dataFetcher.GoogleBillingDataFetcher
import com.qonversion.android.sdk.internal.billing.dataFetcher.GoogleBillingDataFetcherImpl
import com.qonversion.android.sdk.internal.billing.purchaser.GoogleBillingPurchaser
import com.qonversion.android.sdk.internal.billing.purchaser.GoogleBillingPurchaserImpl
import com.qonversion.android.sdk.internal.di.cacher.CacherAssembly
import com.qonversion.android.sdk.internal.di.misc.MiscAssembly
import com.qonversion.android.sdk.internal.di.services.ServicesAssembly
import com.qonversion.android.sdk.internal.di.storage.StorageAssembly
import com.qonversion.android.sdk.internal.user.controller.UserController
import com.qonversion.android.sdk.internal.user.controller.UserControllerImpl
import com.qonversion.android.sdk.internal.userProperties.controller.UserPropertiesController
import com.qonversion.android.sdk.internal.userProperties.controller.UserPropertiesControllerImpl

internal class ControllersAssemblyImpl(
    private val storageAssembly: StorageAssembly,
    private val miscAssembly: MiscAssembly,
    private val servicesAssembly: ServicesAssembly,
    private val cacherAssembly: CacherAssembly,
) : ControllersAssembly {
    override fun userPropertiesController(): UserPropertiesController =
        UserPropertiesControllerImpl(
            pendingPropertiesStorage = storageAssembly.pendingUserPropertiesStorage(),
            sentPropertiesStorage = storageAssembly.sentUserPropertiesStorage(),
            service = servicesAssembly.userPropertiesService(),
            worker = miscAssembly.delayedWorker(),
            logger = miscAssembly.logger()
        )

    override fun googleBillingController(purchasesListener: PurchasesListener): GoogleBillingController =
        GoogleBillingControllerImpl(
            googleBillingConsumer(),
            googleBillingPurchaser(),
            googleBillingDataFetcher(),
            purchasesListener,
            miscAssembly.logger()
        )

    override fun userController(): UserController = UserControllerImpl(
        servicesAssembly.userService(),
        cacherAssembly.userCacher(),
        storageAssembly.userDataStorage(),
        miscAssembly.userIdGenerator(),
        miscAssembly.logger()
    )

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun googleBillingConsumer(): GoogleBillingConsumer =
        GoogleBillingConsumerImpl(miscAssembly.logger())

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun googleBillingPurchaser(): GoogleBillingPurchaser =
        GoogleBillingPurchaserImpl(miscAssembly.logger())

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun googleBillingDataFetcher(): GoogleBillingDataFetcher =
        GoogleBillingDataFetcherImpl(miscAssembly.logger())
}
