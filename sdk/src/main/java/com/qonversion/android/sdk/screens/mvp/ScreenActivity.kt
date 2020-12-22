package com.qonversion.android.sdk.screens.mvp

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import com.qonversion.android.sdk.Qonversion
import com.qonversion.android.sdk.QonversionError
import com.qonversion.android.sdk.QonversionPermissionsCallback
import com.qonversion.android.sdk.R

import com.qonversion.android.sdk.screens.QScreenManager
import com.qonversion.android.sdk.di.QDependencyInjector
import com.qonversion.android.sdk.di.component.DaggerActivityComponent
import com.qonversion.android.sdk.di.module.ActivityModule
import com.qonversion.android.sdk.dto.QPermission
import kotlinx.android.synthetic.main.activity_screen.*
import javax.inject.Inject

class ScreenActivity : AppCompatActivity(), ScreenContract.View {
    @Inject
    lateinit var screenManager: QScreenManager

    @Inject
    lateinit var presenter: ScreenPresenter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_screen)

        injectDependencies()

        configureWebClient()

        loadWebView()

        confirmScreenShow()
    }

    override fun openScreen(screenId: String, htmlPage: String) {
        val intent = Intent(this, ScreenActivity::class.java)
        intent.putExtra(INTENT_HTML_PAGE, htmlPage)
        intent.putExtra(INTENT_SCREEN_ID, screenId)
        startActivity(intent)
    }

    override fun openLink(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        startActivity(intent)
        close()
    }

    override fun purchase(productId: String) {
        Qonversion.purchase(this, productId, object : QonversionPermissionsCallback {
            override fun onSuccess(permissions: Map<String, QPermission>) {
                close()
            }

            override fun onError(error: QonversionError) {
                close()
            }
        })
    }

    override fun restore() {
        Qonversion.restore(object : QonversionPermissionsCallback {
            override fun onSuccess(permissions: Map<String, QPermission>) {
                close()
            }

            override fun onError(error: QonversionError) {
                close()
            }
        })
    }

    override fun close() {
        finish()
    }

    override fun onError(message: String) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Screen show alert")
        builder.setMessage("Failure to show screen.")

        builder.setPositiveButton(android.R.string.yes) { _, _ -> }
        builder.show()
    }

    private fun injectDependencies() {
        DaggerActivityComponent.builder()
            .screenComponent(QDependencyInjector.screenComponent)
            .activityModule(ActivityModule(this))
            .build().inject(this)
    }

    private fun configureWebClient() {
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                return presenter.shouldOverrideUrlLoading(url)
            }

            override fun onPageStarted(
                view: WebView?,
                url: String?,
                favicon: Bitmap?
            ) {
                super.onPageStarted(view, url, favicon)
                progressBar.visibility = ProgressBar.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                progressBar.visibility = ProgressBar.GONE
            }

        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, progress: Int) {
                progressBar.progress = progress
            }
        }
    }

    private fun loadWebView() {
        val extraHtmlPage = intent.getStringExtra(INTENT_HTML_PAGE)
        if (extraHtmlPage != null) {
            webView.loadData(extraHtmlPage, MIME_TYPE, ENCODING)
        }
    }

    private fun confirmScreenShow() {
        val extraScreenId = intent.getStringExtra(INTENT_SCREEN_ID)
        if (extraScreenId != null) {
            presenter.screenShownWithId(extraScreenId)
        }
    }

    companion object {
        const val INTENT_HTML_PAGE = "htmlPage"
        const val INTENT_SCREEN_ID = "screenId"
        private const val MIME_TYPE = "text/html"
        private const val ENCODING = "UTF-8"
    }
}