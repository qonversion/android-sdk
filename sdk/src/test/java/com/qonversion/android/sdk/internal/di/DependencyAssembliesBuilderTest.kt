package com.qonversion.android.sdk.internal.di

import android.app.Application
import com.qonversion.android.sdk.internal.InternalConfig
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

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

        // when and then
        assertDoesNotThrow {
            builder.build()
        }
    }
}
