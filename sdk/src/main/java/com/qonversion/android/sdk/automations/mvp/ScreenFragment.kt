package com.qonversion.android.sdk.automations.mvp

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.fragment.app.Fragment
import com.qonversion.android.sdk.Qonversion
import com.qonversion.android.sdk.automations.dto.QActionResult
import com.qonversion.android.sdk.automations.dto.QActionResultType
import com.qonversion.android.sdk.automations.dto.QScreenPresentationStyle
import com.qonversion.android.sdk.automations.internal.QAutomationsManager
import com.qonversion.android.sdk.automations.internal.macros.ScreenProcessor
import com.qonversion.android.sdk.databinding.QFragmentScreenBinding
import com.qonversion.android.sdk.dto.QEntitlement
import com.qonversion.android.sdk.dto.QonversionError
import com.qonversion.android.sdk.dto.QonversionErrorCode
import com.qonversion.android.sdk.internal.di.QDependencyInjector
import com.qonversion.android.sdk.internal.di.component.DaggerFragmentComponent
import com.qonversion.android.sdk.internal.di.module.FragmentModule
import com.qonversion.android.sdk.internal.logger.ConsoleLogger
import com.qonversion.android.sdk.listeners.QonversionEntitlementsCallback
import javax.inject.Inject

class ScreenFragment : Fragment(), ScreenContract.View {
    @Inject
    internal lateinit var automationsManager: QAutomationsManager

    @Inject
    internal lateinit var presenter: ScreenPresenter

    @Inject
    internal lateinit var screenProcessor: ScreenProcessor

    private var _binding: QFragmentScreenBinding? = null
    private val binding get() = _binding!!

    private val logger = ConsoleLogger()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = QFragmentScreenBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        injectDependencies()

        configureWebClient()

        loadWebView()

        confirmScreenView()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun openScreen(screenId: String, htmlPage: String) {
        val actionResult = QActionResult(QActionResultType.Navigation, getActionResultMap(screenId))
        automationsManager.automationsDidStartExecuting(actionResult)

        try {
            val intent = Intent(context, ScreenActivity::class.java)
            intent.putExtra(EX_HTML_PAGE, htmlPage)
            intent.putExtra(EX_SCREEN_ID, screenId)
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
        binding.progressBarLayout.progressBar.visibility = View.VISIBLE

        activity?.let {
            Qonversion.shared.purchase(
                it,
                productId,
                object : QonversionEntitlementsCallback {
                    override fun onSuccess(entitlements: Map<String, QEntitlement>) =
                        close(actionResult)

                    override fun onError(error: QonversionError) = handleOnErrorCallback(
                        object {}.javaClass.enclosingMethod?.name,
                        error,
                        actionResult
                    )
                })
        }
    }

    override fun restore() {
        val actionResult = QActionResult(QActionResultType.Restore)
        automationsManager.automationsDidStartExecuting(actionResult)
        binding.progressBarLayout.progressBar.visibility = View.VISIBLE

        Qonversion.shared.restore(object : QonversionEntitlementsCallback {
            override fun onSuccess(entitlements: Map<String, QEntitlement>) = close(actionResult)

            override fun onError(error: QonversionError) = handleOnErrorCallback(
                object {}.javaClass.enclosingMethod?.name,
                error,
                actionResult
            )
        })
    }

    override fun close(actionResult: QActionResult) {
        binding.progressBarLayout.progressBar.visibility = View.GONE
        activity?.finish()
        automationsManager.automationsDidFinishExecuting(actionResult)
        automationsManager.automationsFinished()
    }

    override fun onError(error: QonversionError, shouldCloseScreen: Boolean) {
        val builder = AlertDialog.Builder(context)
        builder.setTitle("Failed to show the in-app screen")
        builder.setMessage(error.description)
        builder.setPositiveButton(android.R.string.ok) { _, _ ->
            if (shouldCloseScreen) {
                close()
            }
        }
        builder.show()
    }

    private fun configureWebClient() {
        binding.webView.webViewClient = object : WebViewClient() {
            @Deprecated("Deprecated since API 24", ReplaceWith(""))
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                return presenter.shouldOverrideUrlLoading(url)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                binding.progressBarLayout.progressBar.visibility = View.GONE
                return super.onPageFinished(view, url)
            }
        }
    }

    private fun injectDependencies() {
        DaggerFragmentComponent.builder()
            .appComponent(QDependencyInjector.appComponent)
            .fragmentModule(FragmentModule(this))
            .build().inject(this)
    }

    private fun loadWebView() {
        val extraHtmlPage = arguments?.getString(EX_HTML_PAGE)

        extraHtmlPage?.let {
            screenProcessor.processScreen(it,
                { macrosHtml ->
                    binding.webView.loadDataWithBaseURL(
                        null,
                        macrosHtml,
                        MIME_TYPE,
                        ENCODING,
                        null
                    )
                }, { error ->
                    logger.release("loadWebView() -> Failed to process screen macros ${error.description}")
                    onError(error, true)
                })
        } ?: run {
            logger.release("loadWebView() -> Failed to fetch html page for the app screen")
            onError(QonversionError(QonversionErrorCode.UnknownError), true)
        }
    }

    private fun confirmScreenView() {
        val extraScreenId = arguments?.getString(EX_SCREEN_ID)

        extraScreenId?.let {
            automationsManager.automationsDidShowScreen(extraScreenId)
            presenter.confirmScreenView(it)
        } ?: logger.debug("confirmScreenView() -> Failed to confirm screen view")
    }

    private fun getActionResultMap(value: String): MutableMap<String, String> =
        mutableMapOf(ACTION_MAP_KEY to value)

    private fun handleOnErrorCallback(
        functionName: String?,
        error: QonversionError,
        actionResult: QActionResult
    ) {
        binding.progressBarLayout.progressBar.visibility = View.GONE
        logger.debug("ScreenActivity $functionName -> $error.description")
        actionResult.error = error
        automationsManager.automationsDidFailExecuting(actionResult)
    }

    companion object {
        private const val EX_HTML_PAGE = "htmlPage"
        private const val EX_SCREEN_ID = "screenId"
        private const val MIME_TYPE = "text/html"
        private const val ENCODING = "UTF-8"
        private const val ACTION_MAP_KEY = "value"

        fun getArguments(
            screenId: String?,
            htmlPage: String?
        ) = Bundle().also {
            it.putString(EX_SCREEN_ID, screenId)
            it.putString(EX_HTML_PAGE, htmlPage)
        }
    }
}
