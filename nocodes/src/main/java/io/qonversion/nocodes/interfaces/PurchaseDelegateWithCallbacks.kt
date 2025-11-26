package io.qonversion.nocodes.interfaces

import com.qonversion.android.sdk.dto.products.QProduct

/**
 * The delegate is responsible for handling custom purchase and restore operations.
 * If this delegate is provided, it will be used instead of the default Qonversion SDK purchase flow.
 *
 * This interface uses callback-based methods.
 * For Kotlin coroutines with suspend functions, use [PurchaseDelegate].
 */
interface PurchaseDelegateWithCallbacks {

    /**
     * Callback interface for success operations.
     */
    fun interface OnSuccess {
        /**
         * Invoked when the operation succeeds.
         */
        fun invoke()
    }

    /**
     * Callback interface for error operations.
     */
    fun interface OnError {
        /**
         * Invoked when the operation fails.
         *
         * @param throwable the error that occurred
         */
        fun invoke(throwable: Throwable)
    }

    /**
     * Handle the purchase of a product using callbacks.
     *
     * @param product the product to purchase
     * @param onSuccess callback to be called when purchase succeeds
     * @param onError callback to be called when purchase fails
     */
    fun purchase(
        product: QProduct,
        onSuccess: OnSuccess,
        onError: OnError
    )

    /**
     * Handle the restore of purchases using callbacks.
     *
     * @param onSuccess callback to be called when restore succeeds
     * @param onError callback to be called when restore fails
     */
    fun restore(
        onSuccess: OnSuccess,
        onError: OnError
    )
}

