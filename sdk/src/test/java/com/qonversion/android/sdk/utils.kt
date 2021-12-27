package com.qonversion.android.sdk

import com.qonversion.android.sdk.internal.exception.ErrorCode
import com.qonversion.android.sdk.internal.exception.QonversionException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import java.lang.reflect.Modifier

internal fun assertThatQonversionExceptionThrown(code: ErrorCode? = null, callable: () -> Unit): QonversionException {
    val throwable = try {
        callable()
        null
    } catch (e: Throwable) {
        e
    }

    assertThat(throwable).isInstanceOf(QonversionException::class.java)
    code?.let {
        assertThat((throwable as QonversionException).code).isEqualTo(code)
    }
    return throwable as QonversionException
}

@ExperimentalCoroutinesApi
internal fun coAssertThatQonversionExceptionThrown(code: ErrorCode? = null, callable: suspend () -> Unit): QonversionException {
    return assertThatQonversionExceptionThrown(code) {
        runTest {
            callable()
        }
    }
}
