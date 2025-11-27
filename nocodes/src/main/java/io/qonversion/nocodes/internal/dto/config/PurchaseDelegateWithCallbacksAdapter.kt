package io.qonversion.nocodes.internal.dto.config

import com.qonversion.android.sdk.dto.products.QProduct
import io.qonversion.nocodes.interfaces.PurchaseDelegate
import io.qonversion.nocodes.interfaces.PurchaseDelegateWithCallbacks
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Internal adapter that wraps [PurchaseDelegateWithCallbacks] into [PurchaseDelegate].
 * This allows Java developers to use callbacks while the system uses suspend functions.
 */
internal class PurchaseDelegateWithCallbacksAdapter(
    private val delegate: PurchaseDelegateWithCallbacks
) : PurchaseDelegate {

    override suspend fun purchase(product: QProduct) {
        suspendCancellableCoroutine { continuation ->
            delegate.purchase(
                product,
                onSuccess = { continuation.resume(Unit) },
                onError = { throwable -> continuation.resumeWithException(throwable) }
            )
        }
    }

    override suspend fun restore() {
        suspendCancellableCoroutine { continuation ->
            delegate.restore(
                onSuccess = { continuation.resume(Unit) },
                onError = { throwable -> continuation.resumeWithException(throwable) }
            )
        }
    }
}
