package io.qonversion.android.sdk.internal.di.component

import io.qonversion.android.sdk.internal.di.module.FragmentModule
import io.qonversion.android.sdk.internal.di.scope.ActivityScope
import io.qonversion.android.sdk.automations.mvp.ScreenFragment
import dagger.Component

@ActivityScope
@Component(dependencies = [AppComponent::class], modules = [FragmentModule::class])
internal interface FragmentComponent {
    fun inject(into: ScreenFragment)
}
