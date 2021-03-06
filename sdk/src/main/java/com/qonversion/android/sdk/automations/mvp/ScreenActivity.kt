package com.qonversion.android.sdk.automations.mvp

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import com.qonversion.android.sdk.*
import com.qonversion.android.sdk.automations.QActionResult
import com.qonversion.android.sdk.automations.QActionResultType
import com.qonversion.android.sdk.automations.QAutomationsManager
import com.qonversion.android.sdk.di.QDependencyInjector
import com.qonversion.android.sdk.di.component.DaggerActivityComponent
import com.qonversion.android.sdk.di.module.ActivityModule
import com.qonversion.android.sdk.dto.QPermission
import com.qonversion.android.sdk.logger.ConsoleLogger
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
        val actionResult = QActionResult(QActionResultType.Navigation, getActionResultMap(screenId))
        automationsManager.automationsDidStartExecuting(actionResult)

        try {
            val intent = Intent(this, ScreenActivity::class.java)
            intent.putExtra(INTENT_HTML_PAGE, htmlPage)
            intent.putExtra(INTENT_SCREEN_ID, screenId)
            startActivity(intent)
            automationsManager.automationsDidFinishExecuting(actionResult)
        } catch (e: Exception) {
            automationsManager.automationsDidFailExecuting(actionResult)
        }
    }

    override fun openLink(url: String) {
        val actionResult = QActionResult(QActionResultType.Url, getActionResultMap(url))
        automationsManager.automationsDidStartExecuting(actionResult)

        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
            automationsManager.automationsDidFinishExecuting(actionResult)
        } catch (e: ActivityNotFoundException) {
            logger.release("Couldn't find any Activity to handle the Intent with url $url")
            automationsManager.automationsDidFailExecuting(actionResult)
        }
    }

    override fun openDeepLink(url: String) {
        val actionResult = QActionResult(QActionResultType.DeepLink, getActionResultMap(url))
        automationsManager.automationsDidStartExecuting(actionResult)

        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
            close(QActionResult(QActionResultType.DeepLink, getActionResultMap(url)))
        } catch (e: ActivityNotFoundException) {
            logger.release("Couldn't find any Activity to handle the Intent with deeplink $url")
            automationsManager.automationsDidFailExecuting(actionResult)
        }
    }

    override fun purchase(productId: String) {
        val actionResult = QActionResult(QActionResultType.Purchase, getActionResultMap(productId))
        automationsManager.automationsDidStartExecuting(actionResult)
        progressBar.visibility = View.VISIBLE

        Qonversion.purchase(this, productId, object : QonversionPermissionsCallback {
            override fun onSuccess(permissions: Map<String, QPermission>) = close(actionResult)

            override fun onError(error: QonversionError) = handleOnErrorCallback(object {}.javaClass.enclosingMethod?.name, error, actionResult)
        })
    }

    override fun restore() {
        val actionResult = QActionResult(QActionResultType.Restore)
        automationsManager.automationsDidStartExecuting(actionResult)
        progressBar.visibility = View.VISIBLE

        Qonversion.restore(object : QonversionPermissionsCallback {
            override fun onSuccess(permissions: Map<String, QPermission>) = close(actionResult)

            override fun onError(error: QonversionError) = handleOnErrorCallback(object {}.javaClass.enclosingMethod?.name, error, actionResult)
        })
    }

    override fun close(actionResult: QActionResult) {
        progressBar.visibility = View.GONE
        finish()
        automationsManager.automationsDidFinishExecuting(actionResult)
        automationsManager.automationsFinished()
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
            automationsManager.automationsDidShowScreen(extraScreenId)
            presenter.confirmScreenView(it)
        } ?: logger.debug("confirmScreenView() -> Failure to confirm screen view")
    }

    private fun getActionResultMap(value: String): MutableMap<String, String> =
        mutableMapOf(ACTION_MAP_KEY to value)

    private fun handleOnErrorCallback(
        functionName: String?,
        error: QonversionError,
        actionResult: QActionResult
    ) {
        progressBar.visibility = View.GONE
        logger.debug("ScreenActivity $functionName -> $error.description")
        actionResult.error = error
        automationsManager.automationsDidFailExecuting(actionResult)
    }

    companion object {
        const val INTENT_HTML_PAGE = "htmlPage"
        const val INTENT_SCREEN_ID = "screenId"
        private const val MIME_TYPE = "text/html"
        private const val ENCODING = "UTF-8"
        private const val ACTION_MAP_KEY = "value"
    }
}