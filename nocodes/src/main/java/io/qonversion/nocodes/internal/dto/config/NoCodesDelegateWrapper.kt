package io.qonversion.nocodes.internal.dto.config

import android.os.Handler
import android.os.Looper
import io.qonversion.nocodes.dto.QAction
import io.qonversion.nocodes.interfaces.NoCodesDelegate
import io.qonversion.nocodes.error.NoCodesError
import java.lang.ref.WeakReference

class NoCodesDelegateWrapper(
    private val delegate: WeakReference<NoCodesDelegate>
) : NoCodesDelegate {

    constructor(delegate: NoCodesDelegate) : this(WeakReference(delegate))

    private val mainHandler = Handler(Looper.getMainLooper())
    
    override fun onScreenShown(screenId: String) {
        mainHandler.post {
            delegate.get()?.onScreenShown(screenId)
        }
    }
    
    override fun onActionStartedExecuting(action: QAction) {
        mainHandler.post {
            delegate.get()?.onActionStartedExecuting(action)
        }
    }
    
    override fun onActionFailedToExecute(action: QAction) {
        mainHandler.post {
            delegate.get()?.onActionFailedToExecute(action)
        }
    }
    
    override fun onActionFinishedExecuting(action: QAction) {
        mainHandler.post {
            delegate.get()?.onActionFinishedExecuting(action)
        }
    }
    
    override fun onFinished() {
        mainHandler.post {
            delegate.get()?.onFinished()
        }
    }
    
    override fun onScreenFailedToLoad(error: NoCodesError) {
        mainHandler.post {
            delegate.get()?.onScreenFailedToLoad(error)
        }
    }
}