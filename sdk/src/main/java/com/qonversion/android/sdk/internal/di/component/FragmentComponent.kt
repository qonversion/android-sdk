package com.qonversion.android.sdk.internal.di.component

import com.qonversion.android.sdk.internal.di.module.FragmentModule
import com.qonversion.android.sdk.internal.di.scope.ActivityScope
import com.qonversion.android.sdk.automations.mvp.ScreenFragment
import dagger.Component

@ActivityScope
@Component(dependencies = [AppComponent::class], modules = [FragmentModule::class])
internal interface FragmentComponent {
    fun inject(into: ScreenFragment)
}
