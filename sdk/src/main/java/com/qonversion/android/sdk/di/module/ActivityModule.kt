package com.qonversion.android.sdk.di.module

import com.qonversion.android.sdk.di.scope.ActivityScope
import com.qonversion.android.sdk.push.mvp.ScreenContract
import dagger.Module
import dagger.Provides

@Module
class ActivityModule(private val view: ScreenContract.View) {

    @ActivityScope
    @Provides
    fun provideScreenView(): ScreenContract.View {
        return view
    }
}
