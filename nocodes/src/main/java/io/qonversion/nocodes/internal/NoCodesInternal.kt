package io.qonversion.nocodes.internal

import io.qonversion.nocodes.NoCodes
import io.qonversion.nocodes.dto.LogLevel
import io.qonversion.nocodes.interfaces.NoCodesDelegate
import io.qonversion.nocodes.interfaces.NoCodesShowScreenCallback
import io.qonversion.nocodes.interfaces.ScreenCustomizationDelegate
import io.qonversion.nocodes.internal.di.DependenciesAssembly
import io.qonversion.nocodes.internal.dto.config.InternalConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

internal class NoCodesInternal(
    private val internalConfig: InternalConfig,
    dependenciesAssembly: DependenciesAssembly,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) : NoCodes {

    private val screenController = dependenciesAssembly.screenController()

    override fun setDelegate(delegate: NoCodesDelegate) {
        internalConfig.noCodesDelegate = WeakReference(delegate)
    }

    override fun setScreenCustomizationDelegate(delegate: ScreenCustomizationDelegate) {
        internalConfig.screenCustomizationDelegate = WeakReference(delegate)
    }

    override fun showScreen(screenId: String, callback: NoCodesShowScreenCallback) {
        scope.launch {
            try {
                screenController.showScreen(screenId)
            } catch (e: Exception) {
                // todo converting between NoCodesError / NoCodesException
            }
        }
    }

    override fun setLogLevel(logLevel: LogLevel) {
        internalConfig.loggerConfig = internalConfig.loggerConfig.copy(logLevel = logLevel)
    }

    override fun setLogTag(logTag: String) {
        internalConfig.loggerConfig = internalConfig.loggerConfig.copy(logTag = logTag)
    }
}