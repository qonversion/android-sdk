package io.qonversion.nocodes.internal

import io.qonversion.nocodes.NoCodes
import io.qonversion.nocodes.dto.LogLevel
import io.qonversion.nocodes.dto.NoCodesTheme
import io.qonversion.nocodes.dto.QNoCodeScreen
import io.qonversion.nocodes.error.ErrorCode
import io.qonversion.nocodes.error.NoCodesError
import io.qonversion.nocodes.error.NoCodesException
import io.qonversion.nocodes.interfaces.NoCodesDelegate
import io.qonversion.nocodes.interfaces.NoCodesScreenLoadCallback
import io.qonversion.nocodes.interfaces.PurchaseDelegate
import io.qonversion.nocodes.interfaces.PurchaseDelegateWithCallbacks
import io.qonversion.nocodes.interfaces.CustomVariablesDelegate
import io.qonversion.nocodes.interfaces.ScreenCustomizationDelegate
import io.qonversion.nocodes.internal.di.DependenciesAssembly
import io.qonversion.nocodes.internal.dto.config.InternalConfig
import io.qonversion.nocodes.internal.dto.config.NoCodesDelegateWrapper
import io.qonversion.nocodes.internal.dto.config.PurchaseDelegateWithCallbacksAdapter
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

internal class NoCodesInternal(
    private val internalConfig: InternalConfig,
    dependenciesAssembly: DependenciesAssembly,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) : NoCodes {

    private val screenController = dependenciesAssembly.screenController()
    private val screenService = dependenciesAssembly.screenService()
    private val screenEventsService = dependenciesAssembly.screenEventsService()
    private val logger = dependenciesAssembly.logger()

    init {
        // Automatic screen preloading during initialization
        scope.launch {
            try {
                val preloadedScreens = screenService.preloadScreens()
                logger.info("NoCodesInternal -> Successfully preloaded ${preloadedScreens.size} screens during initialization")
            } catch (e: Exception) {
                logger.warn("NoCodesInternal -> Failed to preload screens during initialization: ${e.message}")
            }
        }
    }

    override fun setDelegate(delegate: NoCodesDelegate) {
        internalConfig.noCodesDelegate = NoCodesDelegateWrapper(delegate)
    }

    override fun setScreenCustomizationDelegate(delegate: ScreenCustomizationDelegate) {
        internalConfig.screenCustomizationDelegate = delegate
    }

    override fun setPurchaseDelegate(delegate: PurchaseDelegate) {
        internalConfig.purchaseDelegate = delegate
    }

    override fun setPurchaseDelegate(delegate: PurchaseDelegateWithCallbacks) {
        internalConfig.purchaseDelegate = PurchaseDelegateWithCallbacksAdapter(delegate)
    }

    override fun setCustomVariablesDelegate(delegate: CustomVariablesDelegate) {
        internalConfig.customVariablesDelegate = delegate
    }

    override fun showScreen(contextKey: String) {
        scope.launch {
            suspendFlushPendingUserProperties()
            screenController.showScreen(contextKey)
        }
    }

    override suspend fun loadScreen(contextKey: String): QNoCodeScreen {
        logger.verbose("loadScreen() -> Loading the screen with the context key $contextKey without presenting")
        // Pure data load, no presentation. Deliberately skips the pending user properties flush
        // (unlike showScreen) since nothing is displayed yet.
        val screen = try {
            screenService.getScreen(contextKey)
        } catch (e: Exception) {
            // Coroutine cancellation must propagate as-is to keep structured concurrency
            // cooperative — wrapping it would turn a caller's cancel into a "network error".
            if (e is CancellationException) throw e
            // The public contract promises NoCodesException, while raw network exceptions
            // may escape the service layer.
            throw e as? NoCodesException
                ?: NoCodesException(ErrorCode.NetworkRequestExecution, e.message, e)
        }
        return QNoCodeScreen(screen.id, screen.contextKey)
    }

    override fun loadScreen(contextKey: String, callback: NoCodesScreenLoadCallback) {
        scope.launch {
            try {
                val screen = loadScreen(contextKey)
                withContext(Dispatchers.Main) {
                    callback.onSuccess(screen)
                }
            } catch (e: NoCodesException) {
                withContext(Dispatchers.Main) {
                    callback.onError(NoCodesError(e))
                }
            }
        }
    }

    private suspend fun suspendFlushPendingUserProperties() {
        kotlin.coroutines.suspendCoroutine<Unit> { continuation ->
            try {
                com.qonversion.android.sdk.Qonversion.shared.forceSendProperties(
                    object : com.qonversion.android.sdk.listeners.QonversionEmptyCallback {
                        override fun onComplete() {
                            continuation.resume(Unit)
                        }
                    }
                )
            } catch (e: Exception) {
                logger.warn("NoCodesInternal -> Failed to flush pending user properties: ${e.message}")
                continuation.resume(Unit)
            }
        }
    }

    override fun close() {
        runBlocking {
            screenEventsService.flushAndWait()
        }
        screenController.close()
    }

    override fun setLogLevel(logLevel: LogLevel) {
        internalConfig.loggerConfig = internalConfig.loggerConfig.copy(logLevel = logLevel)
    }

    override fun setLogTag(logTag: String) {
        internalConfig.loggerConfig = internalConfig.loggerConfig.copy(logTag = logTag)
    }

    override fun setLocale(locale: String?) {
        internalConfig.customLocale = locale
    }

    override fun setTheme(theme: NoCodesTheme) {
        internalConfig.theme = theme
    }
}
