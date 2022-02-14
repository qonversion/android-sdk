package com.qonversion.android.sdk.internal.di

import android.app.Application
import com.qonversion.android.sdk.internal.InternalConfig
import com.qonversion.android.sdk.internal.di.controllers.ControllersAssemblyImpl
import com.qonversion.android.sdk.internal.di.mappers.MappersAssemblyImpl
import com.qonversion.android.sdk.internal.di.misc.MiscAssemblyImpl
import com.qonversion.android.sdk.internal.di.network.NetworkAssemblyImpl
import com.qonversion.android.sdk.internal.di.services.ServicesAssemblyImpl
import com.qonversion.android.sdk.internal.di.storage.StorageAssemblyImpl
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class DependenciesAssemblyBuilderTest {
    private lateinit var builder: DependenciesAssembly.Builder

    private val mockApplication = mockk<Application>()
    private val internalConfig = mockk<InternalConfig>()

    @BeforeEach
    fun setUp() {
        builder = DependenciesAssembly.Builder(mockApplication, internalConfig)
    }

    @Test
    fun `build`() {
        // given

        // when
        val result = builder.build()

        // then
        assertThat(result.controllersAssembly).isInstanceOf(ControllersAssemblyImpl::class.java)
        assertThat(result.mappersAssembly).isInstanceOf(MappersAssemblyImpl::class.java)
        assertThat(result.miscAssembly).isInstanceOf(MiscAssemblyImpl::class.java)
        assertThat(result.networkAssembly).isInstanceOf(NetworkAssemblyImpl::class.java)
        assertThat(result.servicesAssembly).isInstanceOf(ServicesAssemblyImpl::class.java)
        assertThat(result.storageAssembly).isInstanceOf(StorageAssemblyImpl::class.java)
    }
}
