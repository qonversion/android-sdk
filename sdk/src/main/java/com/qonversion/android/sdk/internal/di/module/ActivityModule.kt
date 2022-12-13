package com.qonversion.android.sdk.internal.di.module

import com.qonversion.android.sdk.automations.internal.macros.ScreenProcessor
import com.qonversion.android.sdk.internal.di.scope.ActivityScope
import com.qonversion.android.sdk.automations.mvp.ScreenContract
import dagger.Module
import dagger.Provides

@Module
internal class ActivityModule(private val view: ScreenContract.View) {

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
