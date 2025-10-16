package io.qonversion.nocodes.internal.screen.view

import io.qonversion.nocodes.dto.QAction

internal class ScreenContract {
    interface View {
        fun displayScreen(screenId: String, html: String)

        fun navigateToScreen(screenId: String)

        fun openLink(url: String)

        fun openDeepLink(url: String)

        fun purchase(productId: String, screenId: String?)

        fun restore()

        fun close(action: QAction = QAction(QAction.Type.Close))

        fun closeAll(action: QAction = QAction(QAction.Type.CloseAll))

        fun sendProductsToWebView(jsonData: String)

        fun finishScreenPreparation()
        
        fun showSkeleton()
        
        fun hideSkeleton()
    }

    internal interface Presenter {
        fun onStart(contextKey: String?, screenId: String?)

        fun onWebViewMessageReceived(message: String)
    }
}
