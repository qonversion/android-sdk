package com.qonversion.android.sdk.automations.mvp

import com.qonversion.android.sdk.dto.QonversionError
import com.qonversion.android.sdk.automations.dto.QActionResult
import com.qonversion.android.sdk.automations.dto.QActionResultType

internal class ScreenContract {
    interface View {
        fun openScreen(screenId: String, htmlPage: String)

        fun openLink(url: String)

        fun openDeepLink(url: String)

        fun purchase(productId: String)

        fun restore()

        fun close(actionResult: QActionResult = QActionResult(QActionResultType.Close))

        fun closeAll(actionResult: QActionResult = QActionResult(QActionResultType.Close))

        fun onError(error: QonversionError, shouldCloseScreen: Boolean = false)
    }

    internal interface Presenter {
        fun confirmScreenView(screenId: String)

        fun shouldOverrideUrlLoading(url: String?): Boolean
    }
}
