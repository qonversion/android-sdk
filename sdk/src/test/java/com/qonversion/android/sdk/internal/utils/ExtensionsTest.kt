package com.qonversion.android.sdk.internal.utils

import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.lang.ClassCastException

internal class ExtensionsTest {

    @Nested
    inner class IsDebuggableTest {
        @Test
        fun `is not debuggable`() {
            // given
            val appInfo = ApplicationInfo()
            appInfo.flags = 0
            val context = spyk<Context>()
            every { context.applicationInfo } returns appInfo

            // when
            val res = context.isDebuggable

            // then
            assertThat(res).isFalse
        }

        @Test
        fun `is debuggable`() {
            // given
            val appInfo = ApplicationInfo()
            appInfo.flags = ApplicationInfo.FLAG_DEBUGGABLE
            val context = spyk<Context>()
            every { context.applicationInfo } returns appInfo

            // when
            val res = context.isDebuggable

            // then
            assertThat(res).isTrue
        }
    }

    @Nested
    inner class ContextToApplicationTest {

        @Test
        fun `context is application`() {
            // given
            val context = spyk<Context>()
            val appContext = mockk<Application>()
            every { context.applicationContext } returns appContext

            // when
            val res = context.application

            // then
            assertThat(res).isSameAs(appContext)
        }

        @Test
        fun `context is not application`() {
            // given
            val context = spyk<Context>()
            val appContext = mockk<Context>()
            every { context.applicationContext } returns appContext

            // when and then
            assertThatThrownBy {
                context.application
            }.isInstanceOf(ClassCastException::class.java)
        }
    }
}