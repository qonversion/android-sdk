package com.qonversion.android.sdk.automations.mvp

import android.os.Build
import com.qonversion.android.sdk.dto.QonversionError
import com.qonversion.android.sdk.dto.QonversionErrorCode
import com.qonversion.android.sdk.internal.QonversionRepository
import com.qonversion.android.sdk.internal.dto.automations.Screen
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.O_MR1])
internal class ScreenPresenterTest {
    private val mockRepository = mockk<QonversionRepository>(relaxed = true)
    private val mockView = mockk<ScreenContract.View>(relaxed = true)
    private lateinit var screenPresenter: ScreenPresenter

    @Before
    fun setUp() {
        clearAllMocks()

        screenPresenter = ScreenPresenter(mockRepository, mockView)
    }

    @Test
    fun `shouldOverrideUrlLoading shouldn't call view methods when url is null`() {
        val url = null

        val result = screenPresenter.shouldOverrideUrlLoading(url)

        verifyViewWasNotCalled()
        assertThat(result).isTrue()
    }

    @Test
    fun `shouldOverrideUrlLoading shouldn't call view methods when url host is not automation`() {
        val url = "qon-projectID://auto?action=url&amp;data=https://qonversion.io"

        val result = screenPresenter.shouldOverrideUrlLoading(url)

        verifyViewWasNotCalled()
        assertThat(result).isTrue()
    }

    @Test
    fun `shouldOverrideUrlLoading shouldn't call view methods when url scheme is not qonversion`() {
        val url = "scheme-projectID://automation?action=url&amp;data=https://qonversion.io"

        val result = screenPresenter.shouldOverrideUrlLoading(url)

        verifyViewWasNotCalled()
        assertThat(result).isTrue()
    }

    @Test
    fun `shouldOverrideUrlLoading should call openLink() when action type is url`() {
        val url = "qon-AgLqRhy0://automation?action=url&data=https://qonversion.io"

        val result = screenPresenter.shouldOverrideUrlLoading(url)

        verify(exactly = 1) {
            mockView.openLink("https://qonversion.io")
        }
        verify(exactly = 0) {
            mockView.openScreen(any(), any())
            mockView.purchase(any())
            mockView.restore()
            mockView.close()
            mockView.onError(any())
        }
        assertThat(result).isTrue()
    }

    @Test
    fun `shouldOverrideUrlLoading should call openDeepLink() when action type is deeplink`() {
        val url = "qon-AgLqRhy0://automation?action=deeplink&data=someApp://mainScreen"

        val result = screenPresenter.shouldOverrideUrlLoading(url)

        verify(exactly = 1) {
            mockView.openDeepLink("someApp://mainScreen")
        }
        verify(exactly = 0) {
            mockView.openScreen(any(), any())
            mockView.purchase(any())
            mockView.restore()
            mockView.close()
            mockView.onError(any())
        }
        assertThat(result).isTrue()
    }

    @Test
    fun `shouldOverrideUrlLoading should call purchase() when action type is purchase`() {
        val url = "qon-AgLqRhy0://automation?action=purchase&data=main"

        val result = screenPresenter.shouldOverrideUrlLoading(url)

        verify(exactly = 1) {
            mockView.purchase("main")
        }
        verify(exactly = 0) {
            mockView.openScreen(any(), any())
            mockView.openLink(any())
            mockView.restore()
            mockView.close()
            mockView.onError(any())
        }
        assertThat(result).isTrue()
    }

    @Test
    fun `shouldOverrideUrlLoading should call restore() when action type is restore`() {
        val url = "qon-AgLqRhy0://automation?action=restore"

        val result = screenPresenter.shouldOverrideUrlLoading(url)

        verify(exactly = 1) {
            mockView.restore()
        }
        verify(exactly = 0) {
            mockView.openScreen(any(), any())
            mockView.openLink(any())
            mockView.purchase(any())
            mockView.close()
            mockView.onError(any())
        }
        assertThat(result).isTrue()
    }

    @Test
    fun `shouldOverrideUrlLoading should call close() when action type is close`() {
        val url = "qon-AgLqRhy0://automation?action=close"

        val result = screenPresenter.shouldOverrideUrlLoading(url)

        verify(exactly = 1) {
            mockView.close()
        }
        verify(exactly = 0) {
            mockView.openScreen(any(), any())
            mockView.openLink(any())
            mockView.purchase(any())
            mockView.restore()
            mockView.onError(any())
        }
        assertThat(result).isTrue()
    }

    @Test
    fun `shouldOverrideUrlLoading should call openScreen() when action type is navigate and screens request succeeded`() {
        val screenId = "screen-uid-789-2"
        val url = "qon-AgLqRhy0://automation?action=navigate&data=$screenId"
        val html = "<html><body>Screen 2 Content<body></html>"
        val screen = Screen(screenId, html, "ru", "#CCEEFF", "string")

        every {
            mockRepository.screens(screenId, captureLambda(), any())
        } answers {
            lambda<(Screen) -> Unit>().captured.invoke(
                screen
            )
        }

        val result = screenPresenter.shouldOverrideUrlLoading(url)

        verify(exactly = 1) {
            mockView.openScreen(screenId, html)
        }
        verify(exactly = 0) {
            mockView.openLink(any())
            mockView.purchase(any())
            mockView.restore()
            mockView.close()
            mockView.onError(any())
        }
        assertThat(result).isTrue()
    }

    @Test
    fun `shouldOverrideUrlLoading should call onError() when action type is navigate and screens request failed`() {
        val screenId = "screen-uid-789-2"
        val url = "qon-AgLqRhy0://automation?action=navigate&data=$screenId"
        val error = QonversionError(QonversionErrorCode.BackendError, "Failed to load screen")
        every {
            mockRepository.screens(screenId, any(), captureLambda())
        } answers {
            lambda<(QonversionError) -> Unit>().captured.invoke(
                error
            )
        }

        val result = screenPresenter.shouldOverrideUrlLoading(url)

        verify(exactly = 1) {
            mockView.onError(error)
        }
        verify(exactly = 0) {
            mockView.openScreen(any(), any())
            mockView.openLink(any())
            mockView.purchase(any())
            mockView.restore()
            mockView.close()
        }
        assertThat(result).isTrue()
    }

    @Test
    fun confirmScreenView() {
        val screenId = "screenId"
        screenPresenter.confirmScreenView(screenId)

        verify(exactly = 1) {
            mockRepository.views(screenId)
        }
    }

    private fun verifyViewWasNotCalled() {
        verify(exactly = 0) {
            mockView.openScreen(any(), any())
            mockView.openLink(any())
            mockView.purchase(any())
            mockView.restore()
            mockView.close()
            mockView.onError(any())
        }
    }
}