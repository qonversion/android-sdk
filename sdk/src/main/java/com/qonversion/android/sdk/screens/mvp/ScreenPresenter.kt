package com.qonversion.android.sdk.screens.mvp

import android.net.Uri
import com.qonversion.android.sdk.QonversionError
import com.qonversion.android.sdk.QonversionRepository
import com.qonversion.android.sdk.QonversionScreensCallback
import com.qonversion.android.sdk.screens.QActionType
import javax.inject.Inject

class ScreenPresenter @Inject constructor(
    private val repository: QonversionRepository,
    private val view: ScreenContract.View
) : ScreenContract.Presenter {

    override fun shouldOverrideUrlLoading(url: String?): Boolean {
        if (url == null) {
            return true
        }

        when (getActionTypeFromUrl(url)) {
            QActionType.Url -> {
                val link = getParameterFromUrl(url, LINK)
                if (link != null) {
                    view.openLink(link)
                }
            }
            QActionType.DeepLink -> {
                val deepLink = getParameterFromUrl(url, URL)
                if (deepLink != null) {
                    view.openLink(deepLink)
                }
            }
            QActionType.Close -> {
                view.close()
            }
            QActionType.Navigate -> {
                val screenId = getParameterFromUrl(url, TO)
                if (screenId != null) {
                    getHtmlPageForScreen(screenId)
                }
            }
            QActionType.Purchase -> {
                val productId = getParameterFromUrl(url, PRODUCT)
                if (productId != null) {
                    view.purchase(productId)
                }
            }
            QActionType.Restore -> {
                view.restore()
            }
            else -> return true
        }
        return true
    }

    override fun screenShownWithId(screenId: String){
        repository.views(screenId)
    }

    private fun getActionTypeFromUrl(url: String): QActionType {
        val uri = Uri.parse(url)
        val actionType = uri.getQueryParameter(ACTION)

        return QActionType.fromType(actionType)
    }

    private fun getParameterFromUrl(url: String, name: String): String? {
        val uri = Uri.parse(url)

        return uri.getQueryParameter(name)
    }

    private fun getHtmlPageForScreen(screenId: String) {
        repository.screens(screenId, object : QonversionScreensCallback {
            override fun onSuccess(htmlPage: String) {
                view.openScreen(screenId, htmlPage)
            }

            override fun onError(error: QonversionError) {
                view.onError("Couldn't load the screen with id $screenId")
            }
        })
    }

    companion object {
        private const val ACTION = "action"
        private const val TO = "to"
        private const val PRODUCT = "product"
        private const val URL = "url"
        private const val LINK = "link"
    }
}