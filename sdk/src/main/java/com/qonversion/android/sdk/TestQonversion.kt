package com.qonversion.android.sdk

import android.content.Context
import androidx.preference.PreferenceManager
import com.qonversion.android.sdk.internal.InternalConfig
import com.qonversion.android.sdk.internal.common.API_URL
import com.qonversion.android.sdk.internal.common.localStorage.SharedPreferencesStorage
import com.qonversion.android.sdk.internal.mappers.EntitlementMapper
import com.qonversion.android.sdk.internal.mappers.UserMapper
import com.qonversion.android.sdk.internal.mappers.UserPurchaseMapper
import com.qonversion.android.sdk.internal.networkLayer.headerBuilder.HeaderBuilderImpl
import com.qonversion.android.sdk.internal.networkLayer.apiInteractor.ApiInteractorImpl
import com.qonversion.android.sdk.internal.networkLayer.networkClient.NetworkClientImpl
import com.qonversion.android.sdk.internal.networkLayer.requestConfigurator.RequestConfiguratorImpl
import com.qonversion.android.sdk.internal.networkLayer.requestSerializer.JsonSerializer
import com.qonversion.android.sdk.internal.networkLayer.retryDelayCalculator.ExponentialDelayCalculator
import com.qonversion.android.sdk.internal.user.UserServiceImpl
import com.qonversion.android.sdk.old.billing.getCurrentTimeInMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*
import kotlin.random.Random

object TestQonversion {

    fun test(context: Context) {
        val serializer = JsonSerializer()

        val random = Random(getCurrentTimeInMillis())
        val delayCalculator = ExponentialDelayCalculator(random)
        val config = InternalConfig.apply {
            uid = "QON_rekgejho234f4r3234f"
            projectKey = "PV77YHL7qnGvsdmpTs7gimsxUvY-Znl2"
            sdkVersion = "3.2.1"
        }

        val networkClient = NetworkClientImpl(serializer)
        val apiInteractor = ApiInteractorImpl(networkClient, delayCalculator, config)

        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val localStorage = SharedPreferencesStorage(prefs)

        val headerBuilder = HeaderBuilderImpl(localStorage, Locale.getDefault(), config)
        val requestConfigurator = RequestConfiguratorImpl(headerBuilder, API_URL)

        val userMapper = UserMapper(UserPurchaseMapper(), EntitlementMapper())

        val userService = UserServiceImpl(requestConfigurator, apiInteractor, userMapper, localStorage)

        var e = userService.obtainUserId()
        userService.updateCurrentUserId("erjgwkrw")
        e = userService.obtainUserId()
        val w = userService.logoutIfNeeded()
        e = userService.obtainUserId()
        userService.resetUser()
        e = userService.obtainUserId()

        val user = CoroutineScope(Dispatchers.IO).launch {
            val resp = userService.createUser("QON_85c3ef6a6fc245c89cfc24e6420cc579")
            userService.getUser("QON_85c3ef6a6fc245c89cfc24e6420cc578")
        }
    }
}