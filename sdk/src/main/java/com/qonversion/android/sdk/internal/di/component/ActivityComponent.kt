package com.qonversion.android.sdk.internal.di.component

import com.qonversion.android.sdk.internal.di.module.ActivityModule
import com.qonversion.android.sdk.internal.di.scope.ActivityScope
import com.qonversion.android.sdk.automations.mvp.ScreenActivity
import dagger.Component

@ActivityScope
@Component(dependencies = [AppComponent::class], modules = [ActivityModule::class])
internal interface ActivityComponent {
    fun inject(into: ScreenActivity)
}
