package io.qonversion.nocodes.internal.di.network

import io.qonversion.nocodes.internal.common.API_URL
import io.qonversion.nocodes.internal.di.mappers.MappersAssembly
import io.qonversion.nocodes.internal.di.misc.MiscAssembly
import io.qonversion.nocodes.internal.di.storage.StorageAssembly
import io.qonversion.nocodes.internal.dto.config.InternalConfig
import io.qonversion.nocodes.internal.networkLayer.RetryPolicy
import io.qonversion.nocodes.internal.networkLayer.apiInteractor.ApiInteractor
import io.qonversion.nocodes.internal.networkLayer.apiInteractor.ApiInteractorImpl
import io.qonversion.nocodes.internal.networkLayer.headerBuilder.HeaderBuilder
import io.qonversion.nocodes.internal.networkLayer.headerBuilder.HeaderBuilderImpl
import io.qonversion.nocodes.internal.networkLayer.networkClient.NetworkClient
import io.qonversion.nocodes.internal.networkLayer.networkClient.NetworkClientImpl
import io.qonversion.nocodes.internal.networkLayer.requestConfigurator.RequestConfigurator
import io.qonversion.nocodes.internal.networkLayer.requestConfigurator.RequestConfiguratorImpl

internal class NetworkAssemblyImpl(
    private val internalConfig: InternalConfig,
    private val mappersAssembly: MappersAssembly,
    private val storageAssembly: StorageAssembly,
    private val miscAssembly: MiscAssembly
) : NetworkAssembly {

    override fun networkClient(): NetworkClient = NetworkClientImpl(miscAssembly.jsonSerializer())

    override fun requestConfigurator(): RequestConfigurator = RequestConfiguratorImpl(
        headerBuilder(),
        internalConfig.networkConfig.proxyUrl ?: API_URL
    )

    override fun exponentialApiInteractor(): ApiInteractor =
        apiInteractor(RetryPolicy.Exponential())

    override fun infiniteExponentialApiInteractor(): ApiInteractor =
        apiInteractor(RetryPolicy.InfiniteExponential())

    override fun headerBuilder(): HeaderBuilder = HeaderBuilderImpl(
        storageAssembly.sharedPreferencesStorage(),
        miscAssembly.locale(),
        internalConfig
    )

    private fun apiInteractor(retryPolicy: RetryPolicy): ApiInteractor {
        return ApiInteractorImpl(
            networkClient(),
            miscAssembly.exponentialDelayCalculator(),
            internalConfig,
            mappersAssembly.apiErrorMapper(),
            retryPolicy
        )
    }
}
