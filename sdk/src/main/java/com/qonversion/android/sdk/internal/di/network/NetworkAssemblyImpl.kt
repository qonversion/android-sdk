package com.qonversion.android.sdk.internal.di.network

import androidx.annotation.VisibleForTesting
import com.qonversion.android.sdk.internal.InternalConfig
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
    private val internalConfig: InternalConfig,
    private val mappersAssembly: MappersAssembly,
    private val storageAssembly: StorageAssembly,
    private val miscAssembly: MiscAssembly
) : NetworkAssembly {

    override fun networkClient(): NetworkClient = NetworkClientImpl(miscAssembly.jsonSerializer())

    override fun requestConfigurator(): RequestConfigurator = RequestConfiguratorImpl(
        headerBuilder(),
        BASE_API_URL,
        internalConfig,
        internalConfig
    )

    override fun exponentialApiInteractor(): ApiInteractor =
        apiInteractor(RetryPolicy.Exponential())

    override fun infiniteExponentialApiInteractor(): ApiInteractor =
        apiInteractor(RetryPolicy.InfiniteExponential())

    override fun headerBuilder(): HeaderBuilder = HeaderBuilderImpl(
        storageAssembly.sharedPreferencesStorage(),
        miscAssembly.locale(),
        internalConfig,
        internalConfig,
        internalConfig
    )

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun apiInteractor(retryPolicy: RetryPolicy): ApiInteractor {
        return ApiInteractorImpl(
            networkClient(),
            miscAssembly.exponentialDelayCalculator(),
            internalConfig,
            mappersAssembly.apiErrorMapper(),
            retryPolicy
        )
    }
}
