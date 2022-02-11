package com.qonversion.android.sdk

import com.qonversion.android.sdk.internal.exception.ErrorCode
import com.qonversion.android.sdk.internal.exception.QonversionException
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.lang.Exception

internal class QonversionTest {

    @Test
    fun `initialize`() {
        // given
        val mockQonversionConfig = mockk<QonversionConfig>(relaxed = true)

        // when
        Qonversion.initialize(mockQonversionConfig)

        // then
        assertThat(Qonversion.sharedInstance).isNotNull
    }

    @Test
    fun `shared instance`() {
        // given

        try {
            // when
            Qonversion.sharedInstance
        } catch (exception: Exception) {
            // then
            assertThat(exception).isInstanceOf(QonversionException::class.java)
            assertThat((exception as QonversionException).code).isEqualTo(ErrorCode.NotInitialized)
        }
    }
}
