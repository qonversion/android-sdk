package com.qonversion.android.sdk.di.component

import com.qonversion.android.sdk.di.module.ActivityModule
import com.qonversion.android.sdk.di.scope.ActivityScope
import com.qonversion.android.sdk.automations.mvp.ScreenActivity
import dagger.Component

@ActivityScope
@Component(dependencies = [AppComponent::class], modules = [ActivityModule::class])
interface ActivityComponent {
    fun inject(into: ScreenActivity)
}
