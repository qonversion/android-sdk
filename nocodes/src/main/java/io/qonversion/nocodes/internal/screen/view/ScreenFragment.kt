package io.qonversion.nocodes.internal.screen.view

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.qonversion.android.sdk.Qonversion
import com.qonversion.android.sdk.dto.QPurchaseOptions
import com.qonversion.android.sdk.dto.QPurchaseResult
import com.qonversion.android.sdk.dto.QonversionError
import com.qonversion.android.sdk.dto.entitlements.QEntitlement
import com.qonversion.android.sdk.dto.products.QProduct
import com.qonversion.android.sdk.listeners.QonversionEntitlementsCallback
import com.qonversion.android.sdk.listeners.QonversionProductsCallback
import com.qonversion.android.sdk.listeners.QonversionPurchaseCallback
import io.qonversion.nocodes.databinding.NcFragmentScreenBinding
import io.qonversion.nocodes.dto.NoCodesTheme
import io.qonversion.nocodes.dto.QAction
import io.qonversion.nocodes.error.NoCodesError
import io.qonversion.nocodes.internal.di.DependenciesAssembly
import androidx.core.net.toUri
import com.qonversion.android.sdk.dto.QonversionErrorCode

class ScreenFragment : Fragment(), ScreenContract.View {

    private val presenter = DependenciesAssembly.instance.screenPresenter(this)
    private val logger = DependenciesAssembly.instance.logger()
    private val delegateProvider = DependenciesAssembly.instance.noCodesDelegateProvider()
    private val purchaseDelegateProvider = DependenciesAssembly.instance.purchaseDelegateProvider()
    private val themeProvider = { DependenciesAssembly.instance.theme() }

    private val delegate = delegateProvider.noCodesDelegate

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

        // Apply theme to skeleton
        applyThemeToSkeleton()

        binding?.skeletonView?.showSkeleton()
        binding?.webView?.visibility = View.GONE
        presenter.onStart(
            arguments?.getString(EX_CONTEXT_KEY),
            arguments?.getString(EX_SCREEN_ID)
        )
    }

    private fun applyThemeToSkeleton() {
        val theme = themeProvider()
        val isDark = when (theme) {
            NoCodesTheme.Dark -> true
            NoCodesTheme.Light -> false
            NoCodesTheme.Auto -> {
                // Use system theme
                val nightModeFlags = resources.configuration.uiMode and
                    android.content.res.Configuration.UI_MODE_NIGHT_MASK
                nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES
            }
        }
        binding?.skeletonView?.setDarkTheme(isDark)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        presenter.onScreenClosed()
        binding = null
    }

    override fun displayScreen(screenId: String, html: String) {
        activity?.runOnUiThread {
            binding?.webView?.loadDataWithBaseURL(
                null,
                html,
                MIME_TYPE,
                ENCODING,
                null
            )
            delegate?.onScreenShown(screenId)
        }
    }

    override fun navigateToScreen(screenId: String) {
        val action = QAction(QAction.Type.Navigation, QAction.Parameter.ScreenId, screenId)
        delegate?.onActionStartedExecuting(action)

        try {
            (activity as ScreenActivity).showScreen(null, screenId)
            delegate?.onActionFinishedExecuting(action)
        } catch (e: Exception) {
            delegate?.onActionFailedToExecute(action)
        }
    }

    override fun openLink(url: String) {
        val action = QAction(QAction.Type.Url, QAction.Parameter.Url, url)
        delegate?.onActionStartedExecuting(action)

        try {
            val intent = Intent(Intent.ACTION_VIEW, url.toUri())
            startActivity(intent)
            delegate?.onActionFinishedExecuting(action)
        } catch (e: ActivityNotFoundException) {
            logger.error("ScreenActivity -> Couldn't find any Activity to handle the Intent with url $url")
            delegate?.onActionFailedToExecute(action)
        }
    }

    override fun openDeepLink(url: String) {
        val action = QAction(QAction.Type.DeepLink, QAction.Parameter.Url, url)
        delegate?.onActionStartedExecuting(action)

        try {
            val intent = Intent(Intent.ACTION_VIEW, url.toUri())
            startActivity(intent)
            closeAll(action)
        } catch (e: ActivityNotFoundException) {
            logger.error("ScreenActivity -> Couldn't find any Activity to handle the Intent with deeplink $url")
            delegate?.onActionFailedToExecute(action)
        }
    }

    override fun purchase(productId: String, screenId: String?) {
        binding?.progressBarLayout?.progressBar?.visibility = View.VISIBLE

        val action = QAction(QAction.Type.Purchase, QAction.Parameter.ProductId, productId)
        delegate?.onActionStartedExecuting(action)

        activity?.let {
            Qonversion.shared.products(object : QonversionProductsCallback {
                override fun onSuccess(products: Map<String, QProduct>) {
                    val product = products[productId] ?: run {
                        return sendFailureEvent(action, "Product with id $productId not found")
                    }

                    handlePurchaseForProduct(it, product, action, screenId)
                }

                override fun onError(error: QonversionError) = sendFailureEvent(action, error)
            })
        }
    }

    private fun handlePurchaseForProduct(context: Activity, product: QProduct, action: QAction, screenId: String?) {
        // Check if custom purchase handler delegate is provided
        val purchaseDelegate = purchaseDelegateProvider.purchaseDelegate
        if (purchaseDelegate != null) {
            // Use custom purchase handler with coroutines
            lifecycleScope.launch {
                try {
                    purchaseDelegate.purchase(product)
                    withContext(Dispatchers.Main) {
                        sendSuccessEvent(action)
                    }
                } catch (throwable: Throwable) {
                    withContext(Dispatchers.Main) {
                        val error = NoCodesError.fromClientThrowable(throwable)
                        action.error = error
                        sendFailureEvent(action, error.details ?: "")
                    }
                }
            }
        } else {
            val purchaseOptionsBuilder = QPurchaseOptions.Builder()
            screenId?.let { nonNullScreenId ->
                purchaseOptionsBuilder.setScreenUid(nonNullScreenId)
            }
            val purchaseOptions = purchaseOptionsBuilder.build()

            Qonversion.shared.purchase(
                context,
                product,
                purchaseOptions,
                object : QonversionPurchaseCallback {
                    override fun onResult(result: QPurchaseResult) {
                        if (result.isSuccessful) {
                            sendSuccessEvent(action)
                        } else {
                            val error = result.error ?: QonversionError(
                                code = QonversionErrorCode.Unknown,
                                additionalMessage = "Purchase failed"
                            )
                            sendFailureEvent(action, error)
                        }
                    }
                })
        }
    }

    override fun restore() {
        binding?.progressBarLayout?.progressBar?.visibility = View.VISIBLE

        val action = QAction(QAction.Type.Restore)
        delegate?.onActionStartedExecuting(action)

        // Check if custom purchase handler delegate is provided
        val purchaseDelegate = purchaseDelegateProvider.purchaseDelegate
        if (purchaseDelegate != null) {
            // Use custom restore handler with coroutines
            lifecycleScope.launch {
                try {
                    purchaseDelegate.restore()
                    withContext(Dispatchers.Main) {
                        sendSuccessEvent(action)
                    }
                } catch (throwable: Throwable) {
                    withContext(Dispatchers.Main) {
                        val error = NoCodesError.fromClientThrowable(throwable)
                        action.error = error
                        sendFailureEvent(action, error.details ?: "")
                    }
                }
            }
        } else {
            // Use default Qonversion SDK restore flow
            Qonversion.shared.restore(object : QonversionEntitlementsCallback {
                override fun onSuccess(entitlements: Map<String, QEntitlement>) = sendSuccessEvent(action)

                override fun onError(error: QonversionError) = sendFailureEvent(action, error)
            })
        }
    }

    // MARK: - Success/Failure Event Sending

    /**
     * Sends successEvent to WebView. WebView will handle executing the configured success action.
     */
    private fun sendSuccessEvent(action: QAction) {
        delegate?.onActionFinishedExecuting(action)
        binding?.progressBarLayout?.progressBar?.visibility = View.GONE
        binding?.webView?.evaluateJavascript(
            "window.dispatchEvent(new CustomEvent(\"successEvent\", {detail: {}}))",
            null
        )
    }

    private fun sendFailureEvent(action: QAction, error: QonversionError) {
        action.error = NoCodesError(error)
        sendFailureEvent(action, error.description)
    }

    /**
     * Sends failureEvent to WebView. WebView will handle executing the configured failure action.
     */
    private fun sendFailureEvent(action: QAction, errorMessage: String) {
        binding?.progressBarLayout?.progressBar?.visibility = View.GONE
        binding?.skeletonView?.hideSkeleton()
        logger.error("ScreenFragment -> Action failed: $errorMessage")
        delegate?.onActionFailedToExecute(action)
        binding?.webView?.evaluateJavascript(
            "window.dispatchEvent(new CustomEvent(\"failureEvent\", {detail: {}}))",
            null
        )
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

    override fun sendProductsToWebView(jsonData: String) {
        binding?.webView?.evaluateJavascript("window.dispatchEvent(new CustomEvent(\"setProducts\",  {detail: $jsonData} ))", null)
    }

    override fun finishScreenPreparation() {
        binding?.progressBarLayout?.progressBar?.visibility = View.GONE
        binding?.webView?.visibility = View.VISIBLE
        binding?.skeletonView?.hideSkeleton()
    }

    @JavascriptInterface
    fun jsMessageReceived(message: String) {
        Log.d("JSMessage", message)
        activity?.runOnUiThread {
            presenter.onWebViewMessageReceived(message)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebClient() {
        binding?.webView?.settings?.javaScriptEnabled = true
        binding?.webView?.addJavascriptInterface(this, "NoCodesMessageHandler")
    }

    companion object {
        private const val EX_CONTEXT_KEY = "contextKey"
        private const val EX_SCREEN_ID = "screenId"
        private const val MIME_TYPE = "text/html"
        private const val ENCODING = "UTF-8"

        fun getArguments(contextKey: String?, screenId: String?) = Bundle().also {
            it.putString(EX_CONTEXT_KEY, contextKey)
            it.putString(EX_SCREEN_ID, screenId)
        }
    }
}
