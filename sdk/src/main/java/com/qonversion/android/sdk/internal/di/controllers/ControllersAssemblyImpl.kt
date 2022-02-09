package com.qonversion.android.sdk.internal.di.controllers

import com.qonversion.android.sdk.internal.billing.PurchasesListener
import com.qonversion.android.sdk.internal.billing.consumer.GoogleBillingConsumer
import com.qonversion.android.sdk.internal.billing.consumer.GoogleBillingConsumerImpl
import com.qonversion.android.sdk.internal.billing.controller.GoogleBillingController
import com.qonversion.android.sdk.internal.billing.controller.GoogleBillingControllerImpl
import com.qonversion.android.sdk.internal.billing.dataFetcher.GoogleBillingDataFetcher
import com.qonversion.android.sdk.internal.billing.dataFetcher.GoogleBillingDataFetcherImpl
import com.qonversion.android.sdk.internal.billing.purchaser.GoogleBillingPurchaser
import com.qonversion.android.sdk.internal.billing.purchaser.GoogleBillingPurchaserImpl
import com.qonversion.android.sdk.internal.common.StorageConstants
import com.qonversion.android.sdk.internal.common.mappers.MapDataMapper
import com.qonversion.android.sdk.internal.di.misc.MiscAssembly
import com.qonversion.android.sdk.internal.di.services.ServicesAssembly
import com.qonversion.android.sdk.internal.userProperties.UserPropertiesStorage
import com.qonversion.android.sdk.internal.userProperties.UserPropertiesStorageImpl
import com.qonversion.android.sdk.internal.userProperties.controller.UserPropertiesController
import com.qonversion.android.sdk.internal.userProperties.controller.UserPropertiesControllerImpl
import com.qonversion.android.sdk.internal.utils.workers.DelayedWorker
import com.qonversion.android.sdk.internal.utils.workers.DelayedWorkerImpl

internal object ControllersAssemblyImpl : ControllersAssembly {

    lateinit var servicesAssembly: ServicesAssembly

    lateinit var miscAssembly: MiscAssembly

    override val userPropertiesController: UserPropertiesController
        get() = provideUserPropertiesController()

    override fun getGoogleBillingController(purchasesListener: PurchasesListener): GoogleBillingController {
        return provideGoogleBillingController(purchasesListener)
    }

    override fun init(miscAssembly: MiscAssembly, servicesAssembly: ServicesAssembly) {
        this.miscAssembly = miscAssembly
        this.servicesAssembly = servicesAssembly
    }

    fun provideUserPropertiesController(): UserPropertiesController {
        return UserPropertiesControllerImpl(
            pendingPropertiesStorage = providePropertiesStorage(StorageConstants.PendingUserProperties.key),
            sentPropertiesStorage = providePropertiesStorage(StorageConstants.SentUserProperties.key),
            service = servicesAssembly.userPropertiesService,
            worker = provideDelayedWorker(),
            logger = miscAssembly.logger
        )
    }

    fun providePropertiesStorage(storageName: String): UserPropertiesStorage {
        return UserPropertiesStorageImpl(
            miscAssembly.localStorage,
            provideMapDataMapper(),
            storageName,
            miscAssembly.logger
        )
    }

    fun provideDelayedWorker(): DelayedWorker {
        return DelayedWorkerImpl()
    }

    fun provideMapDataMapper(): MapDataMapper {
        return MapDataMapper()
    }

    fun provideGoogleBillingController(listener: PurchasesListener): GoogleBillingController {
        return GoogleBillingControllerImpl(
            provideGoogleBillingConsumer(),
            provideGoogleBillingPurchaser(),
            provideGoogleBillingDataFetcher(),
            listener,
            miscAssembly.logger
        )
    }

    fun provideGoogleBillingConsumer(): GoogleBillingConsumer {
        return GoogleBillingConsumerImpl(miscAssembly.logger)
    }

    fun provideGoogleBillingPurchaser(): GoogleBillingPurchaser {
        return GoogleBillingPurchaserImpl(miscAssembly.logger)
    }

    fun provideGoogleBillingDataFetcher(): GoogleBillingDataFetcher {
        return GoogleBillingDataFetcherImpl(miscAssembly.logger)
    }
}
