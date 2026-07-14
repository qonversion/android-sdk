package io.qonversion.nocodes.internal.screen.view

import io.qonversion.nocodes.dto.QAction
import io.qonversion.nocodes.dto.QScreenVariable

internal class ScreenContract {
    interface View {
        fun displayScreen(screenId: String, html: String, products: List<String>, variables: List<QScreenVariable>)

        fun navigateToScreen(screenId: String)

        fun openLink(url: String)

        fun openDeepLink(url: String)

        fun purchase(productId: String, screenId: String?)

        fun restore()

        fun close(action: QAction = QAction(QAction.Type.Close))

        fun closeAll(action: QAction = QAction(QAction.Type.CloseAll))

        fun sendProductsToWebView(jsonData: String)

        fun injectProductsContext(jsScript: String)

        fun handleGetContext(variables: List<String>)

        fun finishScreenPreparation()

        fun setVariable(name: String, value: String, completion: () -> Unit = {})

        fun setHasWebPurchaseLoader(value: Boolean)
    }

    internal interface Presenter {
        fun onStart(contextKey: String?, screenId: String?)

        fun onWebViewMessageReceived(message: String)

        fun onScreenClosed()
    }
}
