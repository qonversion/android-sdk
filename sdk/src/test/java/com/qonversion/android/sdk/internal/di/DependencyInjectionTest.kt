package com.qonversion.android.sdk.internal.di

import android.app.Application
import com.qonversion.android.sdk.internal.di.controllers.ControllersAssemblyImpl
import com.qonversion.android.sdk.internal.di.misc.MiscAssemblyImpl
import com.qonversion.android.sdk.internal.di.services.ServicesAssemblyImpl
import io.mockk.*

import org.junit.jupiter.api.Test

internal class DependencyInjectionTest {

    @Test
    fun `init`() {
        // given
        val mockkApplication = mockk<Application>()
        mockkObject(MiscAssemblyImpl)
        mockkObject(ServicesAssemblyImpl)
        mockkObject(ControllersAssemblyImpl)

        // when
        DependencyInjection.init(mockkApplication)

        // then
        verifyOrder {
            MiscAssemblyImpl.init(mockkApplication)
            ServicesAssemblyImpl.init(MiscAssemblyImpl)
            ControllersAssemblyImpl.init(MiscAssemblyImpl, ServicesAssemblyImpl)
        }

        unmockkObject(MiscAssemblyImpl)
        unmockkObject(ServicesAssemblyImpl)
        unmockkObject(ControllersAssemblyImpl)
    }
}
