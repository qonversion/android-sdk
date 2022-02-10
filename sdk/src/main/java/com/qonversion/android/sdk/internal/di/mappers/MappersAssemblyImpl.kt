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
    override val userMapper: UserMapper
        get() = UserMapper(userPurchaseMapper, entitlementMapper)

    override val userPurchaseMapper: UserPurchaseMapper
        get() = UserPurchaseMapper()

    override val entitlementMapper: EntitlementMapper
        get() = EntitlementMapper()

    override val productMapper: ProductMapper
        get() = ProductMapper()

    override val subscriptionMapper: SubscriptionMapper
        get() = SubscriptionMapper()

    override val userPropertiesMapper: UserPropertiesMapper
        get() = UserPropertiesMapper()

    override val mapDataMapper: MapDataMapper
        get() = MapDataMapper()

    override val apiErrorMapper: ErrorResponseMapper
        get() = ApiErrorMapper()
}
