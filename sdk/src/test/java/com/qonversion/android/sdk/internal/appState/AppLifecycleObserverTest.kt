package com.qonversion.android.sdk.internal.appState

import android.app.Activity
import android.os.Bundle
import com.qonversion.android.sdk.mockPrivateField
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class AppLifecycleObserverTest {
    private lateinit var appLifecycleObserver: AppLifecycleObserver
    private val mockActivity = mockk<Activity>()
    private val mockBundle = mockk<Bundle>()

    @BeforeEach
    fun setUp() {
        appLifecycleObserver = AppLifecycleObserver()
    }

    @Nested
    inner class ActivityCallbackTest {
        @Test
        fun `on activity resumed`() {
            // given

            // when
            appLifecycleObserver.onActivityResumed(mockActivity)

            // then
            assertThat(appLifecycleObserver.appState).isEqualTo(AppState.Foreground)
        }

        @Test
        fun `on activity paused`() {
            // given

            // when
            appLifecycleObserver.onActivityPaused(mockActivity)

            // then
            assertThat(appLifecycleObserver.appState).isEqualTo(AppState.Background)
        }

        @Test
        fun `on activity created`() {
            // given

            // when
            appLifecycleObserver.onActivityCreated(mockActivity, mockBundle)

            // then
            assertThat(appLifecycleObserver.appState).isEqualTo(AppState.Background)
        }

        @Test
        fun `on activity started`() {
            // given

            // when
            appLifecycleObserver.onActivityStarted(mockActivity)

            // then
            assertThat(appLifecycleObserver.appState).isEqualTo(AppState.Background)
        }

        @Test
        fun `on activity stopped`() {
            // given

            // when
            appLifecycleObserver.onActivityStopped(mockActivity)

            // then
            assertThat(appLifecycleObserver.appState).isEqualTo(AppState.Background)
        }

        @Test
        fun `on activity save instance state`() {
            // given

            // when
            appLifecycleObserver.onActivitySaveInstanceState(mockActivity, mockBundle)

            // then
            assertThat(appLifecycleObserver.appState).isEqualTo(AppState.Background)
        }

        @Test
        fun `on activity destoyed`() {
            // given

            // when
            appLifecycleObserver.onActivityDestroyed(mockActivity)

            // then
            assertThat(appLifecycleObserver.appState).isEqualTo(AppState.Background)
        }
    }

    @Nested
    inner class IsBackgroundTest {
        private val fieldAppState = "appState"

        @Test
        fun `should return true when app state is in background`() {
            // given
            appLifecycleObserver.mockPrivateField(fieldAppState, AppState.Background)

            // when
            val result = appLifecycleObserver.isBackground()

            // then
            assertThat(result).isTrue
        }

        @Test
        fun `should return false when app state is in foreground`() {
            // given
            appLifecycleObserver.mockPrivateField(fieldAppState, AppState.Foreground)

            // when
            val result = appLifecycleObserver.isBackground()

            // then
            assertThat(result).isFalse
        }
    }
}