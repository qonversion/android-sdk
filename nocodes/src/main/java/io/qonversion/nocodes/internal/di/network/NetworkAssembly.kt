package io.qonversion.nocodes.internal.di.network

import io.qonversion.nocodes.internal.networkLayer.apiInteractor.ApiInteractor
import io.qonversion.nocodes.internal.networkLayer.headerBuilder.HeaderBuilder
import io.qonversion.nocodes.internal.networkLayer.networkClient.NetworkClient
import io.qonversion.nocodes.internal.networkLayer.requestConfigurator.RequestConfigurator

internal interface NetworkAssembly {

    fun networkClient(): NetworkClient

    fun headerBuilder(): HeaderBuilder

    fun requestConfigurator(): RequestConfigurator

    fun exponentialApiInteractor(): ApiInteractor

    fun infiniteExponentialApiInteractor(): ApiInteractor
}