package com.qonversion.android.sdk.old.di.module

import com.qonversion.android.sdk.old.automations.macros.ScreenProcessor
import com.qonversion.android.sdk.old.di.scope.ActivityScope
import com.qonversion.android.sdk.old.automations.mvp.ScreenContract
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
