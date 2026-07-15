package io.qonversion.nocodes.internal.dto.config

import android.os.Handler
import android.os.Looper
import io.qonversion.nocodes.dto.QAction
import io.qonversion.nocodes.dto.QScreenVariable
import io.qonversion.nocodes.interfaces.NoCodesDelegate
import io.qonversion.nocodes.error.NoCodesError

class NoCodesDelegateWrapper(
    private val delegate: NoCodesDelegate
) : NoCodesDelegate {

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onScreenShown(screenId: String, products: List<String>) {
        mainHandler.post {
            delegate.onScreenShown(screenId, products)
        }
    }

    override fun onScreenShown(screenId: String, products: List<String>, variables: List<QScreenVariable>) {
        mainHandler.post {
            delegate.onScreenShown(screenId, products, variables)
        }
    }

    override fun onActionStartedExecuting(action: QAction) {
        mainHandler.post {
            delegate.onActionStartedExecuting(action)
        }
    }

    override fun onActionFailedToExecute(action: QAction) {
        mainHandler.post {
            delegate.onActionFailedToExecute(action)
        }
    }

    override fun onActionFinishedExecuting(action: QAction) {
        mainHandler.post {
            delegate.onActionFinishedExecuting(action)
        }
    }

    override fun onCustomAction(value: String) {
        mainHandler.post {
            delegate.onCustomAction(value)
        }
    }

    override fun onFinished() {
        mainHandler.post {
            delegate.onFinished()
        }
    }

    override fun onScreenFailedToLoad(error: NoCodesError) {
        mainHandler.post {
            delegate.onScreenFailedToLoad(error)
        }
    }
}
