package com.qonversion.android.sdk.internal.di.mappers

import com.qonversion.android.sdk.dto.User
import com.qonversion.android.sdk.dto.UserPurchase
import com.qonversion.android.sdk.dto.Entitlement
import com.qonversion.android.sdk.dto.Product
import com.qonversion.android.sdk.dto.Subscription
import com.qonversion.android.sdk.internal.common.mappers.Mapper
import com.qonversion.android.sdk.internal.common.mappers.error.ErrorResponseMapper

internal interface MappersAssembly {

    fun userMapper(): Mapper<User?>

    fun userPurchaseMapper(): Mapper<UserPurchase?>

    fun entitlementMapper(): Mapper<Entitlement?>

    fun productMapper(): Mapper<Product?>

    fun subscriptionMapper(): Mapper<Subscription?>

    fun userPropertiesMapper(): Mapper<List<String>>

    fun mapDataMapper(): Mapper<String>

    fun apiErrorMapper(): ErrorResponseMapper
}
