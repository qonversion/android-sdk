package com.qonversion.android.sdk.internal.appState

import android.app.Activity
import android.os.Bundle
import com.qonversion.android.sdk.mockPrivateField
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class AppLifecyclerObserverTest {
    private lateinit var appLifecyclerObserver: AppLifecyclerObserver
    private val mockActivity = mockk<Activity>()
    private val mockBundle = mockk<Bundle>()

    @BeforeEach
    fun setUp() {
        appLifecyclerObserver = AppLifecyclerObserver()
    }

    @Nested
    inner class ActivityCallbackTest {
        @Test
        fun `on activity resumed`() {
            // given

            // when
            appLifecyclerObserver.onActivityResumed(mockActivity)

            // then
            assertThat(appLifecyclerObserver.appState).isEqualTo(AppState.Foreground)
        }

        @Test
        fun `on activity paused`() {
            // given

            // when
            appLifecyclerObserver.onActivityPaused(mockActivity)

            // then
            assertThat(appLifecyclerObserver.appState).isEqualTo(AppState.Background)
        }

        @Test
        fun `on activity created`() {
            // given

            // when
            appLifecyclerObserver.onActivityCreated(mockActivity, mockBundle)

            // then
            assertThat(appLifecyclerObserver.appState).isEqualTo(AppState.Background)
        }

        @Test
        fun `on activity started`() {
            // given

            // when
            appLifecyclerObserver.onActivityStarted(mockActivity)

            // then
            assertThat(appLifecyclerObserver.appState).isEqualTo(AppState.Background)
        }

        @Test
        fun `on activity stopped`() {
            // given

            // when
            appLifecyclerObserver.onActivityStopped(mockActivity)

            // then
            assertThat(appLifecyclerObserver.appState).isEqualTo(AppState.Background)
        }

        @Test
        fun `on activity save instance state`() {
            // given

            // when
            appLifecyclerObserver.onActivitySaveInstanceState(mockActivity, mockBundle)

            // then
            assertThat(appLifecyclerObserver.appState).isEqualTo(AppState.Background)
        }

        @Test
        fun `on activity destoyed`() {
            // given

            // when
            appLifecyclerObserver.onActivityDestroyed(mockActivity)

            // then
            assertThat(appLifecyclerObserver.appState).isEqualTo(AppState.Background)
        }
    }

    @Nested
    inner class IsBackgroundTest {
        private val fieldAppState = "appState"

        @Test
        fun `should return true when app state is in background`() {
            // given
            appLifecyclerObserver.mockPrivateField(fieldAppState, AppState.Background)

            // when
            val result = appLifecyclerObserver.isBackground()

            // then
            assertThat(result).isTrue
        }

        @Test
        fun `should return false when app state is in foreground`() {
            // given
            appLifecyclerObserver.mockPrivateField(fieldAppState, AppState.Foreground)

            // when
            val result = appLifecyclerObserver.isBackground()

            // then
            assertThat(result).isFalse
        }
    }
}