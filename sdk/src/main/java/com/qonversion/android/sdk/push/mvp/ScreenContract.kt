package com.qonversion.android.sdk.push.mvp

class ScreenContract {
    interface View {
        fun openScreen(screenId: String, htmlPage: String)

        fun openLink(url: String)

        fun purchase(productId: String)

        fun restore()

        fun close()

        fun onError(message: String)
    }

    internal interface Presenter {
        fun screenShownWithId(screenId:String)

        fun shouldOverrideUrlLoading(url: String?): Boolean
    }
}