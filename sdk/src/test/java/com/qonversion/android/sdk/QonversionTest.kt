package com.qonversion.android.sdk

import com.qonversion.android.sdk.internal.exception.ErrorCode
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class QonversionTest {

    @Test
    fun `initialize qonversion and get shared instance`() {
        // given
        val mockQonversionConfig = mockk<QonversionConfig>(relaxed = true)

        assertThatQonversionExceptionThrown(ErrorCode.NotInitialized) {
            Qonversion.sharedInstance
        }

        // when
        Qonversion.initialize(mockQonversionConfig)

        // then
        assertThat(Qonversion.sharedInstance).isNotNull
    }
}
