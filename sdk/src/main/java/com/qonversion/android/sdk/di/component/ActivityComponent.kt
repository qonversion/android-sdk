package com.qonversion.android.sdk.di.component

import com.qonversion.android.sdk.push.mvp.ScreenActivity
import com.qonversion.android.sdk.di.module.ActivityModule
import com.qonversion.android.sdk.di.scope.ActivityScope
import dagger.Component

@ActivityScope
@Component(dependencies = [AutomationComponent::class], modules = [ActivityModule::class])
interface ActivityComponent {
    fun inject(into: ScreenActivity)
}