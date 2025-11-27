package io.qonversion.nocodes.interfaces

import com.qonversion.android.sdk.dto.products.QProduct

/**
 * This delegate should be used if you want to handle purchases and restore operations on your end.
 * If this delegate is provided, it will be used instead of the default Qonversion SDK purchase flow.
 *
 * This interface uses Kotlin coroutines with suspend functions.
 * For Java compatibility, use [PurchaseDelegateWithCallbacks] which provides callback-based methods.
 */
interface PurchaseDelegate {

    /**
     * Handle the purchase of a product.
     *
     * @param product the product to purchase
     * @throws Throwable if the purchase fails. The error will be wrapped in NoCodesError and returned to [NoCodesDelegate].
     */
    suspend fun purchase(product: QProduct)

    /**
     * Handle the restore of purchases.
     *
     * @throws Throwable if the restore fails. The error will be wrapped in NoCodesError and returned to [NoCodesDelegate].
     */
    suspend fun restore()
}
