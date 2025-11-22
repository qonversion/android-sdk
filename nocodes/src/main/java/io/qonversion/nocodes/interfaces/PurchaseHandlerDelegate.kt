package io.qonversion.nocodes.interfaces

import com.qonversion.android.sdk.dto.products.QProduct

/**
 * The delegate is responsible for handling custom purchase and restore operations.
 * If this delegate is provided, it will be used instead of the default Qonversion SDK purchase flow.
 *
 * This interface supports both Kotlin and Java usage through callback-based methods.
 * For Kotlin coroutines, use extension functions provided in this package.
 */
interface PurchaseHandlerDelegate {

    /**
     * Handle the purchase of a product.
     *
     * @param product the product to purchase
     * @param onSuccess callback to be called when purchase succeeds. Should be called to close the no-code screen.
     * @param onError callback to be called when purchase fails. Receives a Throwable that will be wrapped in NoCodesError.
     */
    fun purchase(
        product: QProduct,
        onSuccess: () -> Unit,
        onError: (Throwable?) -> Unit
    )

    /**
     * Handle the restore of purchases.
     *
     * @param onSuccess callback to be called when restore succeeds. Should be called to close the no-code screen.
     * @param onError callback to be called when restore fails. Receives a Throwable that will be wrapped in NoCodesError.
     */
    fun restore(
        onSuccess: () -> Unit,
        onError: (Throwable?) -> Unit
    )
}
