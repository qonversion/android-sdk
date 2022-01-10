package com.qonversion.android.sdk.internal.appState

import android.app.Activity
import android.app.Application
import android.os.Bundle
import io.mockk.mockk
import io.mockk.verify
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
        private val mockActivity = mockk<Activity>()
        private val mockBundle = mockk<Bundle>()

        @Test
        fun `on activity created`() {
            // given
            appLifecycleObserver.appState = AppState.Background

            // when
            appLifecycleObserver.onActivityCreated(mockActivity, mockBundle)

            // then
            assertThat(appLifecycleObserver.appState).isEqualTo(AppState.Background)
        }

        @Test
        fun `on activity started`() {
            // given
            appLifecycleObserver.appState = AppState.Background

            // when
            appLifecycleObserver.onActivityStarted(mockActivity)

            // then
            assertThat(appLifecycleObserver.appState).isEqualTo(AppState.Foreground)
        }

        @Test
        fun `on activity resumed`() {
            // given
            appLifecycleObserver.appState = AppState.Background

            // when
            appLifecycleObserver.onActivityResumed(mockActivity)

            // then
            assertThat(appLifecycleObserver.appState).isEqualTo(AppState.Background)
        }

        @Test
        fun `on activity paused`() {
            // given
            appLifecycleObserver.appState = AppState.Background

            // when
            appLifecycleObserver.onActivityPaused(mockActivity)

            // then
            assertThat(appLifecycleObserver.appState).isEqualTo(AppState.Background)
        }

        @Test
        fun `on activity stopped`() {
            // given
            appLifecycleObserver.appState = AppState.Foreground

            // when
            appLifecycleObserver.onActivityStopped(mockActivity)

            // then
            assertThat(appLifecycleObserver.appState).isEqualTo(AppState.Background)
        }

        @Test
        fun `on activity save instance state`() {
            // given
            appLifecycleObserver.appState = AppState.Background

            // when
            appLifecycleObserver.onActivitySaveInstanceState(mockActivity, mockBundle)

            // then
            assertThat(appLifecycleObserver.appState).isEqualTo(AppState.Background)
        }

        @Test
        fun `on activity destoyed`() {
            // given
            appLifecycleObserver.appState = AppState.Background

            // when
            appLifecycleObserver.onActivityDestroyed(mockActivity)

            // then
            assertThat(appLifecycleObserver.appState).isEqualTo(AppState.Background)
        }
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

    @Nested
    inner class RegisterTest {
        private val mockApplication = mockk<Application>(relaxed = true)

        @Test
        fun `should register activity lifecycle callbacks`() {
            // given

            // when
            appLifecycleObserver.register(mockApplication)

            // then
            verify(exactly = 1) {
                mockApplication.registerActivityLifecycleCallbacks(appLifecycleObserver)
            }
        }
    }
}
