package io.qonversion.nocodes.internal.screen.view

import io.qonversion.nocodes.dto.QAction

internal class ScreenContract {
    interface View {
        fun openScreen(screenId: String, htmlPage: String)

        fun openLink(url: String)

        fun openDeepLink(url: String)

        fun purchase(productId: String)

        fun restore()

        fun close(action: QAction = QAction(QAction.Type.Close))

        fun closeAll(action: QAction = QAction(QAction.Type.Close))

        fun onError(message: String, shouldCloseScreen: Boolean = false)
    }

    internal interface Presenter {
        fun shouldOverrideUrlLoading(url: String?): Boolean
    }
}
