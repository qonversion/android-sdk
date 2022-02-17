package com.qonversion.android.sdk.internal.appState

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.lang.ref.WeakReference

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

    @Nested
    inner class ListenersTest {

        private val listener = mockk<AppStateChangeListener>()

        @Test
        fun `add listener`() {
            // given

            // when
            appLifecycleObserver.addListener(listener)

            // then
            assertThat(appLifecycleObserver.appStateChangeListeners.first()).isInstanceOf(WeakReference::class.java)
            assertThat(appLifecycleObserver.appStateChangeListeners.first().get()).isSameAs(listener)
        }

        @Test
        fun `remove existing listener`() {
            // given
            appLifecycleObserver.appStateChangeListeners.add(WeakReference(listener))

            // when
            appLifecycleObserver.removeListener(listener)

            // then
            assertThat(appLifecycleObserver.appStateChangeListeners).isEmpty()
        }

        @Test
        fun `remove non-existing listener`() {
            // given
            val weakListener = WeakReference(listener)
            appLifecycleObserver.appStateChangeListeners.add(weakListener)
            val expectedResult = setOf(weakListener)

            // when
            appLifecycleObserver.removeListener(mockk())

            // then
            assertThat(appLifecycleObserver.appStateChangeListeners).isEqualTo(expectedResult)
        }

        @Test
        fun `fire to several listeners`() {
            // given
            val secondListener = mockk<AppStateChangeListener>()
            every { listener.onAppBackground() } just runs
            every { secondListener.onAppBackground() } just runs
            appLifecycleObserver.appStateChangeListeners.addAll(
                setOf(
                    WeakReference(listener),
                    WeakReference(secondListener)
                )
            )

            // when
            appLifecycleObserver.fireToListeners { it.onAppBackground() }

            // then
            verify {
                listener.onAppBackground()
                secondListener.onAppBackground()
            }
        }

        @Test
        fun `fire to died listener`() {
            // given
            every { listener.onAppBackground() } just runs
            appLifecycleObserver.appStateChangeListeners.addAll(
                setOf(
                    WeakReference(listener),
                    WeakReference(null)
                )
            )

            // when
            appLifecycleObserver.fireToListeners { it.onAppBackground() }

            // then
            verify {
                listener.onAppBackground()
            }
        }

        @Test
        fun `fire to empty listeners`() {
            // given
            appLifecycleObserver.appStateChangeListeners.clear()

            // when and then
            assertDoesNotThrow {
                appLifecycleObserver.fireToListeners { it.onAppBackground() }
            }
        }
    }
}
