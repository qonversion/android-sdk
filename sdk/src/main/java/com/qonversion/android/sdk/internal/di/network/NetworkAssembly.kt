package com.qonversion.android.sdk.internal.di.network

import com.qonversion.android.sdk.internal.networkLayer.apiInteractor.ApiInteractor
import com.qonversion.android.sdk.internal.networkLayer.headerBuilder.HeaderBuilder
import com.qonversion.android.sdk.internal.networkLayer.networkClient.NetworkClient
import com.qonversion.android.sdk.internal.networkLayer.requestConfigurator.RequestConfigurator

internal interface NetworkAssembly {

    fun networkClient(): NetworkClient

    fun headerBuilder(): HeaderBuilder

    fun requestConfigurator(): RequestConfigurator

    fun exponentialApiInteractor(): ApiInteractor

    fun infiniteExponentialApiInteractor(): ApiInteractor
}
