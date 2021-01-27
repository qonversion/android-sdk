package com.qonversion.android.sdk.push.mvp

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.qonversion.android.sdk.Qonversion
import com.qonversion.android.sdk.QonversionError
import com.qonversion.android.sdk.QonversionPermissionsCallback
import com.qonversion.android.sdk.R
import com.qonversion.android.sdk.di.QDependencyInjector
import com.qonversion.android.sdk.di.component.DaggerActivityComponent
import com.qonversion.android.sdk.di.module.ActivityModule
import com.qonversion.android.sdk.dto.QPermission
import com.qonversion.android.sdk.logger.ConsoleLogger
import com.qonversion.android.sdk.push.QActionResult
import com.qonversion.android.sdk.push.QActionResultType
import com.qonversion.android.sdk.push.QAutomationsManager
import kotlinx.android.synthetic.main.activity_screen.*
import javax.inject.Inject

class ScreenActivity : AppCompatActivity(), ScreenContract.View {
    @Inject
    lateinit var automationsManager: QAutomationsManager

    @Inject
    lateinit var presenter: ScreenPresenter

    private val logger = ConsoleLogger()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_screen)

        injectDependencies()

        configureWebClient()

        loadWebView()

        confirmScreenView()
    }

    override fun openScreen(screenId: String, htmlPage: String) {
        val intent = Intent(this, ScreenActivity::class.java)
        intent.putExtra(INTENT_HTML_PAGE, htmlPage)
        intent.putExtra(INTENT_SCREEN_ID, screenId)
        startActivity(intent)
    }

    override fun openLink(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            logger.release("Couldn't find any Activity to handle the Intent with url $url")
        }
    }

    override fun openDeepLink(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        try {
            startActivity(intent)
            close(QActionResult(QActionResultType.DeepLink, getActionResultMap(url)))
        } catch (e: ActivityNotFoundException) {
            logger.release("Couldn't find any Activity to handle the Intent with deeplink $url")
        }
    }

    override fun purchase(productId: String) {
        Qonversion.purchase(this, productId, object : QonversionPermissionsCallback {
            override fun onSuccess(permissions: Map<String, QPermission>) {
                close(QActionResult(QActionResultType.Purchase, getActionResultMap(productId)))
            }

            override fun onError(error: QonversionError) {
                Toast.makeText(this@ScreenActivity, error.description, Toast.LENGTH_LONG).show()
                logger.release("ScreenActivity purchase() -> $error.description")
            }
        })
    }

    override fun restore() {
        Qonversion.restore(object : QonversionPermissionsCallback {
            override fun onSuccess(permissions: Map<String, QPermission>) {
                close(QActionResult(QActionResultType.Restore))
            }

            override fun onError(error: QonversionError) {
                logger.release("ScreenActivity restore() -> $error.description")
                Toast.makeText(this@ScreenActivity, error.description, Toast.LENGTH_LONG).show()
            }
        })
    }

    override fun close(finalAction: QActionResult) {
        finish()
        automationsManager.automationFinishedWithAction(finalAction)
    }

    override fun onError(error: QonversionError) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Failure to show app screen")
        builder.setMessage(error.description)
        builder.setPositiveButton(android.R.string.yes) { _, _ -> }
        builder.show()
    }

    private fun injectDependencies() {
        DaggerActivityComponent.builder()
            .appComponent(QDependencyInjector.appComponent)
            .activityModule(ActivityModule(this))
            .build().inject(this)
    }

    private fun configureWebClient() {
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                return presenter.shouldOverrideUrlLoading(url)
            }
        }
    }

    private fun loadWebView() {
        val extraHtmlPage = intent.getStringExtra(INTENT_HTML_PAGE)

        extraHtmlPage?.let {
            webView.loadDataWithBaseURL(null, it, MIME_TYPE, ENCODING, null)
        } ?: logger.release("loadWebView() -> Failure to fetch html page for app screen")
    }

    private fun confirmScreenView() {
        val extraScreenId = intent.getStringExtra(INTENT_SCREEN_ID)

        extraScreenId?.let {
            presenter.confirmScreenView(it)
        } ?: logger.debug("confirmScreenView() -> Failure to confirm screen view")
    }

    private fun getActionResultMap(value: String): MutableMap<String, String> = mutableMapOf(ACTION_MAP_KEY to value)

    companion object {
        const val INTENT_HTML_PAGE = "htmlPage"
        const val INTENT_SCREEN_ID = "screenId"
        private const val MIME_TYPE = "text/html"
        private const val ENCODING = "UTF-8"
        private const val ACTION_MAP_KEY = "value"
    }
}