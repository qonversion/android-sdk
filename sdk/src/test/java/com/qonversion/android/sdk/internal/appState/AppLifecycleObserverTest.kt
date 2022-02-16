package com.qonversion.android.sdk.internal.appState

import android.app.Activity
import android.os.Bundle
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class AppLifecycleObserverTest {
    private lateinit var appLifecycleObserver: AppLifecycleObserverImpl

    @BeforeEach
    fun setUp() {
        appLifecycleObserver = AppLifecycleObserverImpl()
    }

    @Nested
    inner class ActivityCallbackTest {
    }

    @Nested
    inner class IsInBackgroundTest {
        @Test
        fun `should return true when app state is in background`() {
            // given
            appLifecycleObserver.appState = AppState.Background

            // when
            val result = appLifecycleObserver.isInBackground()

            // then
            assertThat(result).isTrue
        }

        @Test
        fun `should return false when app state is in foreground`() {
            // given
            appLifecycleObserver.appState = AppState.Foreground

            // when
            val result = appLifecycleObserver.isInBackground()

            // then
            assertThat(result).isFalse
        }
    }
}
