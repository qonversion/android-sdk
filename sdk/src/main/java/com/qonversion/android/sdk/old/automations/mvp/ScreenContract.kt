package com.qonversion.android.sdk.old.automations.mvp

import com.qonversion.android.sdk.old.QonversionError
import com.qonversion.android.sdk.old.automations.QActionResult
import com.qonversion.android.sdk.old.automations.QActionResultType

class ScreenContract {
    interface View {
        fun openScreen(screenId: String, htmlPage: String)

        fun openLink(url: String)

        fun openDeepLink(url: String)

        fun purchase(productId: String)

        fun restore()

        fun close(actionResult: QActionResult = QActionResult(QActionResultType.Close))

        fun onError(error: QonversionError, shouldCloseActivity: Boolean = false)
    }

    internal interface Presenter {
        fun confirmScreenView(screenId: String)

        fun shouldOverrideUrlLoading(url: String?): Boolean
    }
}
