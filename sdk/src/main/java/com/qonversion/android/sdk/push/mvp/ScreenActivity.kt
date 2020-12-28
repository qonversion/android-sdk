package com.qonversion.android.sdk.push.mvp

import android.app.AlertDialog
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
import com.qonversion.android.sdk.push.QAction
import com.qonversion.android.sdk.push.QActionType
import com.qonversion.android.sdk.push.QAutomationManager
import kotlinx.android.synthetic.main.activity_screen.*
import javax.inject.Inject

class ScreenActivity : AppCompatActivity(), ScreenContract.View {
    @Inject
    lateinit var automationManager: QAutomationManager

    @Inject
    lateinit var presenter: ScreenPresenter

    private val logger = ConsoleLogger()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_screen)

        injectDependencies()

        configureWebClient()

        loadWebView()

        confirmScreenIsShown()
    }

    override fun openScreen(screenId: String, htmlPage: String) {
        val intent = Intent(this, ScreenActivity::class.java)
        intent.putExtra(INTENT_HTML_PAGE, htmlPage)
        intent.putExtra(INTENT_SCREEN_ID, screenId)
        startActivity(intent)
    }

    override fun openLink(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            logger.release("Couldn't find the Activity to handle Intent with deeplink")
        }
    }

    override fun purchase(productId: String) {
        Qonversion.purchase(this, productId, object : QonversionPermissionsCallback {
            override fun onSuccess(permissions: Map<String, QPermission>) {
                val map = mutableMapOf<String, String>()
                map["value"] = productId
                automationManager.automationFlowFinishedWithAction(QAction(QActionType.Purchase, map))
                close()
            }

            override fun onError(error: QonversionError) {
                Toast.makeText(this@ScreenActivity, error.description, Toast.LENGTH_LONG).show()
                logger.release("screen purchase() -> $error.description")
            }
        })
    }

    override fun restore() {
        Qonversion.restore(object : QonversionPermissionsCallback {
            override fun onSuccess(permissions: Map<String, QPermission>) {
                automationManager.automationFlowFinishedWithAction(QAction(QActionType.Restore))
                close()
            }

            override fun onError(error: QonversionError) {
                logger.release("screen restore() -> $error.description")
                Toast.makeText(this@ScreenActivity, error.description, Toast.LENGTH_LONG).show()
            }
        })
    }

    override fun close() {
        finish()
    }

    override fun onError(error: QonversionError) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Screen show alert")
        builder.setMessage("Failure to show screen. ${error.description}")

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
        if (extraHtmlPage != null) {
            webView.loadData(extraHtmlPage, MIME_TYPE, ENCODING)
        } else {
            logger.release("loadWebView() -> Failure to fetch html page for screen")
        }
    }

    private fun confirmScreenIsShown() {
        val extraScreenId = intent.getStringExtra(INTENT_SCREEN_ID)
        if (extraScreenId != null) {
            presenter.screenIsShownWithId(extraScreenId)
        } else {
            logger.release("confirmScreenShow() -> Failure to confirm screen shown")
        }
    }

    companion object {
        const val INTENT_HTML_PAGE = "htmlPage"
        const val INTENT_SCREEN_ID = "screenId"
        private const val MIME_TYPE = "text/html"
        private const val ENCODING = "UTF-8"
    }
}