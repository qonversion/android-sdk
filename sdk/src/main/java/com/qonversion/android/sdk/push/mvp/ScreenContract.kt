package com.qonversion.android.sdk.push.mvp

import com.qonversion.android.sdk.QonversionError
import com.qonversion.android.sdk.push.QActionResult
import com.qonversion.android.sdk.push.QActionResultType

class ScreenContract {
    interface View {
        fun openScreen(screenId: String, htmlPage: String)

        fun openLink(url: String)

        fun openDeepLink(url: String)

        fun purchase(productId: String)

        fun restore()

        fun close(finalAction: QActionResult = QActionResult(QActionResultType.Close))

        fun onError(error: QonversionError)
    }

    internal interface Presenter {
        fun confirmScreenView(screenId:String)

        fun shouldOverrideUrlLoading(url: String?): Boolean
    }
}