package com.qonversion.android.sdk.old.di.component

import com.qonversion.android.sdk.old.di.module.ActivityModule
import com.qonversion.android.sdk.old.di.scope.ActivityScope
import com.qonversion.android.sdk.old.automations.mvp.ScreenActivity
import dagger.Component

@ActivityScope
@Component(dependencies = [AppComponent::class], modules = [ActivityModule::class])
interface ActivityComponent {
    fun inject(into: ScreenActivity)
}
