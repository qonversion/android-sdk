package com.qonversion.android.sdk.di.module

import com.qonversion.android.sdk.screens.mvp.ScreenContract
import com.qonversion.android.sdk.di.scope.ActivityScope
import dagger.Module
import dagger.Provides

@Module
class ActivityModule(private val view: ScreenContract.View) {

    @ActivityScope
    @Provides
    fun provideMainView(): ScreenContract.View {
        return view
    }
}
