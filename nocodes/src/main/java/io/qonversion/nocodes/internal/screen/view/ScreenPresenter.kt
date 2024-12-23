package io.qonversion.nocodes.internal.screen.view

import android.net.Uri
import io.qonversion.nocodes.dto.QAction
import io.qonversion.nocodes.internal.screen.service.ScreenService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

internal class ScreenPresenter(
    private val service: ScreenService,
    private val view: ScreenContract.View,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
) : ScreenContract.Presenter {

    override fun shouldOverrideUrlLoading(url: String?): Boolean {

        if (url == null) {
            return true
        }

        val uri = Uri.parse(url)
        if (!uri.shouldOverrideUrlLoading()) {
            return true
        }

        when (uri.getActionType()) {
            QAction.Type.Url -> {
                val link = uri.getData()
                if (link != null) {
                    view.openLink(link)
                }
            }
            QAction.Type.DeepLink -> {
                val deepLink = uri.getData()
                if (deepLink != null) {
                    view.openDeepLink(deepLink)
                }
            }
            QAction.Type.Close -> {
                view.close()
            }
            QAction.Type.CloseAll -> {
                view.closeAll()
            }
            QAction.Type.Navigation -> {
                val screenId = uri.getData()
                if (screenId != null) {
                    loadNextScreen(screenId)
                }
            }
            QAction.Type.Purchase -> {
                val productId = uri.getData()
                if (productId != null) {
                    view.purchase(productId)
                }
            }
            QAction.Type.Restore -> {
                view.restore()
            }
            else -> return true
        }
        return true
    }

    private fun Uri.getActionType(): QAction.Type {
        val actionType = getQueryParameter(ACTION)
        return QAction.Type.fromType(actionType)
    }

    private fun Uri.getData() = getQueryParameter(DATA)

    private fun Uri.shouldOverrideUrlLoading() = isAutomationsHost() && isQScheme()

    private fun Uri.isQScheme(): Boolean {
        val uriScheme = scheme
        if (uriScheme != null) {
            val pattern = REGEX.toRegex()
            return pattern.matches(uriScheme)
        }
        return false
    }

    private fun Uri.isAutomationsHost() = host.equals(HOST)

    private fun loadNextScreen(screenId: String) {
        try {
            scope.launch {
                val screen = service.getScreen(screenId)
                view.openScreen(screenId, screen.body)
            }
        } catch (e: Exception) {
            view.onError("Something went wrong while loading the next screen")
        }
    }

    companion object {
        private const val ACTION = "action"
        private const val DATA = "data"
        private const val SCHEMA = "qon-"
        private const val HOST = "automation"
        private const val REGEX = "$SCHEMA.+"
    }
}
