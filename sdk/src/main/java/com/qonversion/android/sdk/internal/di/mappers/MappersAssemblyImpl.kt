package com.qonversion.android.sdk.internal.di.mappers

import com.qonversion.android.sdk.dto.User
import com.qonversion.android.sdk.dto.UserPurchase
import com.qonversion.android.sdk.dto.Entitlement
import com.qonversion.android.sdk.dto.Product
import com.qonversion.android.sdk.dto.Subscription
import com.qonversion.android.sdk.internal.common.mappers.Mapper
import com.qonversion.android.sdk.internal.common.mappers.UserPurchaseMapper
import com.qonversion.android.sdk.internal.common.mappers.EntitlementMapper
import com.qonversion.android.sdk.internal.common.mappers.ProductMapper
import com.qonversion.android.sdk.internal.common.mappers.SubscriptionMapper
import com.qonversion.android.sdk.internal.common.mappers.UserPropertiesMapper
import com.qonversion.android.sdk.internal.common.mappers.MapDataMapper
import com.qonversion.android.sdk.internal.common.mappers.UserMapper
import com.qonversion.android.sdk.internal.common.mappers.error.ApiErrorMapper
import com.qonversion.android.sdk.internal.common.mappers.error.ErrorResponseMapper

internal class MappersAssemblyImpl : MappersAssembly {
    override fun userMapper(): Mapper<User?> =
        UserMapper(userPurchaseMapper(), entitlementMapper())

    override fun userPurchaseMapper(): Mapper<UserPurchase?> = UserPurchaseMapper()

    override fun entitlementMapper(): Mapper<Entitlement?> = EntitlementMapper()

    override fun productMapper(): Mapper<Product?> = ProductMapper()

    override fun subscriptionMapper(): Mapper<Subscription?> = SubscriptionMapper()

    override fun userPropertiesMapper(): Mapper<List<String>> = UserPropertiesMapper()

    override fun mapDataMapper(): Mapper<String> = MapDataMapper()

    override fun apiErrorMapper(): ErrorResponseMapper = ApiErrorMapper()
}
