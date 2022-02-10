package com.qonversion.android.sdk.internal.di.network

import com.qonversion.android.sdk.internal.networkLayer.RetryPolicy
import com.qonversion.android.sdk.internal.networkLayer.apiInteractor.ApiInteractor
import com.qonversion.android.sdk.internal.networkLayer.headerBuilder.HeaderBuilder
import com.qonversion.android.sdk.internal.networkLayer.networkClient.NetworkClient
import com.qonversion.android.sdk.internal.networkLayer.requestConfigurator.RequestConfigurator

internal interface NetworkAssembly {

    val networkClient: NetworkClient

    val headerBuilder: HeaderBuilder

    val requestConfigurator: RequestConfigurator

    fun getApiInteractor(retryPolicy: RetryPolicy): ApiInteractor
}
