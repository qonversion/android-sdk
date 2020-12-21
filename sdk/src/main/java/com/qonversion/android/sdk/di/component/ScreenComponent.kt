package com.qonversion.android.sdk.di.component

import com.qonversion.android.sdk.Qonversion
import com.qonversion.android.sdk.QonversionRepository

import com.qonversion.android.sdk.screens.QScreenManager
import com.qonversion.android.sdk.di.module.ScreenModule
import com.qonversion.android.sdk.di.scope.ActionScope
import com.qonversion.android.sdk.logger.Logger
import dagger.Component

@ActionScope
@Component(dependencies = [AppComponent::class], modules = [ScreenModule::class])
interface ScreenComponent {
    fun repository(): QonversionRepository
    fun logger(): Logger
    fun actionManager(): QScreenManager
    fun inject(into: Qonversion)
}