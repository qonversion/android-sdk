package com.qonversion.android.sdk.internal.di.network

import com.qonversion.android.sdk.internal.common.BASE_API_URL
import com.qonversion.android.sdk.internal.di.mappers.MappersAssembly
import com.qonversion.android.sdk.internal.di.misc.MiscAssembly
import com.qonversion.android.sdk.internal.di.storage.StorageAssembly
import com.qonversion.android.sdk.internal.networkLayer.RetryPolicy
import com.qonversion.android.sdk.internal.networkLayer.apiInteractor.ApiInteractor
import com.qonversion.android.sdk.internal.networkLayer.apiInteractor.ApiInteractorImpl
import com.qonversion.android.sdk.internal.networkLayer.headerBuilder.HeaderBuilder
import com.qonversion.android.sdk.internal.networkLayer.headerBuilder.HeaderBuilderImpl
import com.qonversion.android.sdk.internal.networkLayer.networkClient.NetworkClient
import com.qonversion.android.sdk.internal.networkLayer.networkClient.NetworkClientImpl
import com.qonversion.android.sdk.internal.networkLayer.requestConfigurator.RequestConfigurator
import com.qonversion.android.sdk.internal.networkLayer.requestConfigurator.RequestConfiguratorImpl

internal class NetworkAssemblyImpl(
    private val mappersAssembly: MappersAssembly,
    private val storageAssembly: StorageAssembly,
    private val miscAssembly: MiscAssembly
) : NetworkAssembly {

    override val networkClient: NetworkClient
        get() = NetworkClientImpl(miscAssembly.jsonSerializer)

    override fun getApiInteractor(retryPolicy: RetryPolicy): ApiInteractor {
        return ApiInteractorImpl(
            networkClient,
            miscAssembly.exponentialDelayCalculator,
            miscAssembly.internalConfig,
            mappersAssembly.apiErrorMapper,
            retryPolicy
        )
    }

    override val requestConfigurator: RequestConfigurator
        get() = RequestConfiguratorImpl(
            headerBuilder,
            BASE_API_URL,
            miscAssembly.internalConfig,
            miscAssembly.internalConfig
        )

    override val headerBuilder: HeaderBuilder
        get() = HeaderBuilderImpl(
            storageAssembly.sharedPreferencesStorage,
            miscAssembly.locale,
            miscAssembly.internalConfig,
            miscAssembly.internalConfig,
            miscAssembly.internalConfig
        )
}
