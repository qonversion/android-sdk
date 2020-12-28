package com.qonversion.android.sdk.push.mvp

import android.os.Build
import com.qonversion.android.sdk.QonversionError
import com.qonversion.android.sdk.QonversionErrorCode
import com.qonversion.android.sdk.QonversionRepository
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
class ScreenPresenterTest {
    private val mockRepository = mockk<QonversionRepository>(relaxed = true)
    private val mockView = mockk<ScreenContract.View>(relaxed = true)
    private lateinit var screenPresenter: ScreenPresenter

    @Before
    fun setUp() {
        clearAllMocks()

        screenPresenter = ScreenPresenter(mockRepository, mockView)
    }

    @Test
    fun `shouldOverrideUrlLoading when url is null`() {
        val url = null

        val result = screenPresenter.shouldOverrideUrlLoading(url)

        verifyViewWasNotCalled()
        assertThat(result).isTrue()
    }

    @Test
    fun `shouldOverrideUrlLoading when url host is not automation`() {
        val url = "q-projectID://auto?action=url&amp;data=https://qonversion.io"

        val result = screenPresenter.shouldOverrideUrlLoading(url)

        verifyViewWasNotCalled()
        assertThat(result).isTrue()
    }

    @Test
    fun `shouldOverrideUrlLoading when url scheme is not qonversion`() {
        val url = "qonversion-projectID://automations?action=url&amp;data=https://qonversion.io"

        val result = screenPresenter.shouldOverrideUrlLoading(url)

        verifyViewWasNotCalled()
        assertThat(result).isTrue()
    }

    @Test
    fun `shouldOverrideUrlLoading when action type is url`() {
        val url = "q-AgLqRhy0://automations?action=url&data=https://qonversion.io"

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
    fun `shouldOverrideUrlLoading when action type is deeplink`() {
        val url = "q-AgLqRhy0://automations?action=deeplink&data=someApp://mainScreen"

        val result = screenPresenter.shouldOverrideUrlLoading(url)

        verify(exactly = 1) {
            mockView.openLink("someApp://mainScreen")
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
    fun `shouldOverrideUrlLoading when action type is purchase`() {
        val url = "q-AgLqRhy0://automations?action=purchase&data=main"

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
    fun `shouldOverrideUrlLoading when action type is restore`() {
        val url = "q-AgLqRhy0://automations?action=restore"

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
    fun `shouldOverrideUrlLoading when action type is close`() {
        val url = "q-AgLqRhy0://automations?action=close"

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
    fun `shouldOverrideUrlLoading when action type is navigate success`() {
        val screenId = "screen-uid-789-2"
        val url = "q-AgLqRhy0://automations?action=navigate&data=$screenId"
        val html = "<html><body>Screen 2 Content<body></html>"

        every {
            mockRepository.screens(screenId, captureLambda(), any())
        } answers {
            lambda<(String) -> Unit>().captured.invoke(
                html
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
    fun `shouldOverrideUrlLoading when action type is navigate error`() {
        val screenId = "screen-uid-789-2"
        val url = "q-AgLqRhy0://automations?action=navigate&data=$screenId"
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
    fun screenShownWithId() {
        val screenId = "screenId"
        screenPresenter.screenIsShownWithId(screenId)

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