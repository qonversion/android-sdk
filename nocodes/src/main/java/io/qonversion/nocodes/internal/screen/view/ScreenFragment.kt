package io.qonversion.nocodes.internal.screen.view

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
import com.qonversion.android.sdk.dto.QPurchaseOptions
import com.qonversion.android.sdk.dto.QonversionError
import com.qonversion.android.sdk.dto.entitlements.QEntitlement
import com.qonversion.android.sdk.dto.products.QProduct
import com.qonversion.android.sdk.listeners.QonversionEntitlementsCallback
import com.qonversion.android.sdk.listeners.QonversionProductsCallback
import io.qonversion.nocodes.databinding.NcFragmentScreenBinding
import io.qonversion.nocodes.dto.QAction
import io.qonversion.nocodes.error.NoCodesError
import io.qonversion.nocodes.internal.di.DependenciesAssembly

class ScreenFragment : Fragment(), ScreenContract.View {

    private val presenter = DependenciesAssembly.instance.screenPresenter(this)
    private val logger = DependenciesAssembly.instance.logger()
    private val delegateProvider = DependenciesAssembly.instance.noCodesDelegateProvider()

    private val delegate = delegateProvider.noCodesDelegate?.get()

//    @Inject
//    internal lateinit var screenProcessor: ScreenProcessor

    private var binding: NcFragmentScreenBinding? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = NcFragmentScreenBinding.inflate(inflater, container, false)
        this.binding = binding
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        configureWebClient()

        loadWebView()
        notifyScreenView()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    override fun openScreen(screenId: String, htmlPage: String) {
        val action = QAction(QAction.Type.Navigation, QAction.Parameter.ScreenId, screenId)
        delegate?.onActionStartedExecuting(action)

        try {
            (activity as ScreenActivity).showScreen(screenId, htmlPage)
            delegate?.onActionFinishedExecuting(action)
        } catch (e: Exception) {
            delegate?.onActionFailedExecuting(action)
        }
    }

    override fun openLink(url: String) {
        val action = QAction(QAction.Type.Url, QAction.Parameter.Url, url)
        delegate?.onActionStartedExecuting(action)

        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
            delegate?.onActionFinishedExecuting(action)
        } catch (e: ActivityNotFoundException) {
            logger.error("ScreenActivity -> Couldn't find any Activity to handle the Intent with url $url")
            delegate?.onActionFailedExecuting(action)
        }
    }

    override fun openDeepLink(url: String) {
        val action = QAction(QAction.Type.DeepLink, QAction.Parameter.Url, url)
        delegate?.onActionStartedExecuting(action)

        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
            close(action)
        } catch (e: ActivityNotFoundException) {
            logger.error("ScreenActivity -> Couldn't find any Activity to handle the Intent with deeplink $url")
            delegate?.onActionFailedExecuting(action)
        }
    }

    override fun purchase(productId: String) {
        val action = QAction(QAction.Type.Purchase, QAction.Parameter.ProductId, productId)
        delegate?.onActionStartedExecuting(action)
        binding?.progressBarLayout?.progressBar?.visibility = View.VISIBLE

        activity?.let {
            Qonversion.shared.products(object : QonversionProductsCallback {
                override fun onSuccess(products: Map<String, QProduct>) {
                    val product = products[productId] ?: run {
                        return handleOnErrorCallback(
                            object {}.javaClass.enclosingMethod?.name,
                            "Product with id $productId not found",
                            action
                        )
                    }

                    val purchaseOptionsBuilder = QPurchaseOptions.Builder()
                    arguments?.getString(EX_SCREEN_ID)?.let { screenUid ->
                        purchaseOptionsBuilder.setScreenUid(screenUid)
                    }
                    val purchaseOptions = purchaseOptionsBuilder.build()

                    Qonversion.shared.purchase(
                        it,
                        product,
                        purchaseOptions,
                        object : QonversionEntitlementsCallback {
                            override fun onSuccess(entitlements: Map<String, QEntitlement>) =
                                close(action)

                            override fun onError(error: QonversionError) = handleOnErrorCallback(
                                object {}.javaClass.enclosingMethod?.name,
                                error,
                                action
                            )
                        })
                }

                override fun onError(error: QonversionError) = handleOnErrorCallback(
                    object {}.javaClass.enclosingMethod?.name,
                    error,
                    action
                )
            })
        }
    }

    override fun restore() {
        val action = QAction(QAction.Type.Restore)
        delegate?.onActionStartedExecuting(action)
        binding?.progressBarLayout?.progressBar?.visibility = View.VISIBLE

        Qonversion.shared.restore(object : QonversionEntitlementsCallback {
            override fun onSuccess(entitlements: Map<String, QEntitlement>) = close(action)

            override fun onError(error: QonversionError) = handleOnErrorCallback(
                object {}.javaClass.enclosingMethod?.name,
                error,
                action
            )
        })
    }

    override fun close(action: QAction) {
        binding?.progressBarLayout?.progressBar?.visibility = View.GONE
        val wasLast = (activity as? ScreenActivity)?.goBack() ?: false
        delegate?.onActionFinishedExecuting(action)

        if (wasLast) {
            delegate?.onFinished()
        }
    }

    override fun closeAll(action: QAction) {
        binding?.progressBarLayout?.progressBar?.visibility = View.GONE
        activity?.finish()
        delegate?.onActionFinishedExecuting(action)
        delegate?.onFinished()
    }

    override fun onError(message: String, shouldCloseScreen: Boolean) {
        val builder = AlertDialog.Builder(context)
        builder.setTitle("Failed to show the in-app screen")
        builder.setMessage(message)
        builder.setPositiveButton(android.R.string.ok) { _, _ ->
            if (shouldCloseScreen) {
                close()
            }
        }
        builder.show()
    }

    private fun configureWebClient() {
        binding?.webView?.webViewClient = object : WebViewClient() {
            @Deprecated("Deprecated since API 24", ReplaceWith(""))
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                return presenter.shouldOverrideUrlLoading(url)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                binding?.progressBarLayout?.progressBar?.visibility = View.GONE
                return super.onPageFinished(view, url)
            }
        }
    }

    private fun loadWebView() {
        val extraHtmlPage = arguments?.getString(EX_HTML_PAGE)

        extraHtmlPage?.let {
//            screenProcessor.processScreen(it,
//                { macrosHtml ->
                    binding?.webView?.loadDataWithBaseURL(
                        null,
//                        macrosHtml,
                        it,
                        MIME_TYPE,
                        ENCODING,
                        null
                    )
//                }, { error ->
//                    logger.error("loadWebView() -> Failed to process screen macros ${error.description}")
//                    onError(error, true)
//                })
        } ?: run {
//            logger.error("loadWebView() -> Failed to fetch html page for the app screen")
//            onError(QonversionError(QonversionErrorCode.Unknown), true)
        }
    }

    private fun notifyScreenView() {
        arguments?.getString(EX_SCREEN_ID)?.let {
            delegate?.onScreenShown(it)
        }
    }

    private fun handleOnErrorCallback(
        functionName: String?,
        error: QonversionError,
        actionResult: QAction
    ) {
        actionResult.error = NoCodesError(error)

        handleOnErrorCallback(
            functionName,
            error.description,
            actionResult
        )
    }

    private fun handleOnErrorCallback(
        functionName: String?,
        description: String,
        actionResult: QAction
    ) {
        binding?.progressBarLayout?.progressBar?.visibility = View.GONE
        logger.error("ScreenActivity $functionName -> $description")
        delegate?.onActionFailedExecuting(actionResult)
    }

    companion object {
        private const val EX_HTML_PAGE = "htmlPage"
        private const val EX_SCREEN_ID = "screenId"
        private const val MIME_TYPE = "text/html"
        private const val ENCODING = "UTF-8"

        fun getArguments(
            screenId: String?,
            htmlPage: String?
        ) = Bundle().also {
            it.putString(EX_SCREEN_ID, screenId)
            it.putString(EX_HTML_PAGE, htmlPage)
        }
    }
}
