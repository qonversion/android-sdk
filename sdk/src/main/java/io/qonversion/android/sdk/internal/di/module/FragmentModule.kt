package io.qonversion.android.sdk.internal.di.module

import io.qonversion.android.sdk.automations.internal.macros.ScreenProcessor
import io.qonversion.android.sdk.internal.di.scope.ActivityScope
import io.qonversion.android.sdk.automations.mvp.ScreenContract
import dagger.Module
import dagger.Provides

@Module
internal class FragmentModule(private val view: ScreenContract.View) {

    @ActivityScope
    @Provides
    fun provideScreenView(): ScreenContract.View {
        return view
    }

    @ActivityScope
    @Provides
    fun provideScreenProcessor(): ScreenProcessor {
        return ScreenProcessor()
    }
}
