package com.qonversion.android.sdk.internal.appState

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AppStateChangeListenerTest {

    private val listener = object : AppStateChangeListener {}

    @Test
    fun `on app foreground default`() {
        // given

        // when
        val res = listener.onAppForeground(true)

        // then
        assertThat(res).isEqualTo(Unit)
    }

    @Test
    fun `on app background default`() {
        // given

        // when
        val res = listener.onAppBackground()

        // then
        assertThat(res).isEqualTo(Unit)
    }
}