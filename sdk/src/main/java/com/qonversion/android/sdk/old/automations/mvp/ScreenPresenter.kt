package com.qonversion.android.sdk.old.automations.mvp

import android.net.Uri
import com.qonversion.android.sdk.old.QonversionRepository
import com.qonversion.android.sdk.old.logger.ConsoleLogger
import com.qonversion.android.sdk.old.automations.QActionResultType
import javax.inject.Inject

class ScreenPresenter @Inject constructor(
    private val repository: QonversionRepository,
    private val view: ScreenContract.View
) : ScreenContract.Presenter {

    private val logger = ConsoleLogger()

    override fun shouldOverrideUrlLoading(url: String?): Boolean {
        logger.debug("shouldOverrideUrlLoading() -> url:$url")

        if (url == null) {
            return true
        }

        val uri = Uri.parse(url)
        if (!uri.shouldOverrideUrlLoading()) {
            return true
        }

        when (uri.getActionType()) {
            QActionResultType.Url -> {
                val link = uri.getData()
                if (link != null) {
                    view.openLink(link)
                }
            }
            QActionResultType.DeepLink -> {
                val deepLink = uri.getData()
                if (deepLink != null) {
                    view.openDeepLink(deepLink)
                }
            }
            QActionResultType.Close -> {
                view.close()
            }
            QActionResultType.Navigation -> {
                val screenId = uri.getData()
                if (screenId != null) {
                    getHtmlPageForScreen(screenId)
                }
            }
            QActionResultType.Purchase -> {
                val productId = uri.getData()
                if (productId != null) {
                    view.purchase(productId)
                }
            }
            QActionResultType.Restore -> {
                view.restore()
            }
            else -> return true
        }
        return true
    }

    override fun confirmScreenView(screenId: String) {
        repository.views(screenId)
    }

    private fun Uri.getActionType(): QActionResultType {
        val actionType = getQueryParameter(ACTION)
        return QActionResultType.fromType(actionType)
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

    private fun getHtmlPageForScreen(screenId: String) {
        repository.screens(screenId,
            { screen ->
                view.openScreen(screenId, screen.htmlPage)
            },
            {
                view.onError(it)
            }
        )
    }

    companion object {
        private const val ACTION = "action"
        private const val DATA = "data"
        private const val SCHEMA = "qon-"
        private const val HOST = "automation"
        private const val REGEX = "$SCHEMA.+"
    }
}
