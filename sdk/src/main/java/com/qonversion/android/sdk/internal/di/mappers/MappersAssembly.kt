package com.qonversion.android.sdk.internal.di.mappers

import com.qonversion.android.sdk.internal.common.mappers.EntitlementMapper
import com.qonversion.android.sdk.internal.common.mappers.MapDataMapper
import com.qonversion.android.sdk.internal.common.mappers.ProductMapper
import com.qonversion.android.sdk.internal.common.mappers.SubscriptionMapper
import com.qonversion.android.sdk.internal.common.mappers.UserMapper
import com.qonversion.android.sdk.internal.common.mappers.UserPropertiesMapper
import com.qonversion.android.sdk.internal.common.mappers.UserPurchaseMapper
import com.qonversion.android.sdk.internal.common.mappers.error.ErrorResponseMapper

internal interface MappersAssembly {

    fun userMapper(): UserMapper

    fun userPurchaseMapper(): UserPurchaseMapper

    fun entitlementMapper(): EntitlementMapper

    fun productMapper(): ProductMapper

    fun subscriptionMapper(): SubscriptionMapper

    fun userPropertiesMapper(): UserPropertiesMapper

    fun mapDataMapper(): MapDataMapper

    fun apiErrorMapper(): ErrorResponseMapper
}
