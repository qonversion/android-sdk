package com.qonversion.android.sdk.di.module

import com.qonversion.android.sdk.automations.macros.ScreenProcessor
import com.qonversion.android.sdk.di.scope.ActivityScope
import com.qonversion.android.sdk.automations.mvp.ScreenContract
import dagger.Module
import dagger.Provides

@Module
class ActivityModule(private val view: ScreenContract.View) {

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
