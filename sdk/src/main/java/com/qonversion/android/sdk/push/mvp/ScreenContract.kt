package com.qonversion.android.sdk.push.mvp

import com.qonversion.android.sdk.QonversionError

class ScreenContract {
    interface View {
        fun openScreen(screenId: String, htmlPage: String)

        fun openLink(url: String)

        fun purchase(productId: String)

        fun restore()

        fun close()

        fun onError(error: QonversionError)
    }

    internal interface Presenter {
        fun screenIsShownWithId(screenId:String)

        fun shouldOverrideUrlLoading(url: String?): Boolean
    }
}