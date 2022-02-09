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
        DependencyInjection.initialize(mockkApplication)

        // then
        verifyOrder {
            MiscAssemblyImpl.initialize(mockkApplication)
            ServicesAssemblyImpl.initialize(MiscAssemblyImpl)
            ControllersAssemblyImpl.initialize(MiscAssemblyImpl, ServicesAssemblyImpl)
        }

        unmockkObject(MiscAssemblyImpl)
        unmockkObject(ServicesAssemblyImpl)
        unmockkObject(ControllersAssemblyImpl)
    }
}
