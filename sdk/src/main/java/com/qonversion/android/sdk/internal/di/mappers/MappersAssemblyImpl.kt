package com.qonversion.android.sdk.internal.di.mappers

import com.qonversion.android.sdk.internal.common.mappers.EntitlementMapper
import com.qonversion.android.sdk.internal.common.mappers.MapDataMapper
import com.qonversion.android.sdk.internal.common.mappers.ProductMapper
import com.qonversion.android.sdk.internal.common.mappers.SubscriptionMapper
import com.qonversion.android.sdk.internal.common.mappers.UserMapper
import com.qonversion.android.sdk.internal.common.mappers.UserPropertiesMapper
import com.qonversion.android.sdk.internal.common.mappers.UserPurchaseMapper
import com.qonversion.android.sdk.internal.common.mappers.error.ApiErrorMapper
import com.qonversion.android.sdk.internal.common.mappers.error.ErrorResponseMapper

internal class MappersAssemblyImpl : MappersAssembly {
    override fun userMapper(): UserMapper =
        UserMapper(userPurchaseMapper(), entitlementMapper())

    override fun userPurchaseMapper(): UserPurchaseMapper = UserPurchaseMapper()

    override fun entitlementMapper(): EntitlementMapper = EntitlementMapper()

    override fun productMapper(): ProductMapper = ProductMapper()

    override fun subscriptionMapper(): SubscriptionMapper = SubscriptionMapper()

    override fun userPropertiesMapper(): UserPropertiesMapper = UserPropertiesMapper()

    override fun mapDataMapper(): MapDataMapper = MapDataMapper()

    override fun apiErrorMapper(): ErrorResponseMapper = ApiErrorMapper()
}
