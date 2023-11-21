package com.qonversion.android.sdk.internal.billing

import android.os.Handler
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.qonversion.android.sdk.internal.logger.Logger

internal class BillingClientHolder(
    private val mainHandler: Handler,
    private val logger: Logger
) : BillingClientStateListener, PurchasesUpdatedListener {

    private var billingClient: BillingClient? = null

    private var purchasesUpdatedListener: PurchasesUpdatedListener? = null

    private var connectionListener: ConnectionListener? = null

    val isConnected get() = billingClient?.isReady == true

    fun startConnection(listener: ConnectionListener) {
        connectionListener = listener

        mainHandler.post {
            synchronized(this@BillingClientHolder) {
                billingClient?.startConnection(this)
                logger.debug("startConnection() -> for $billingClient")
            }
        }
    }

    fun withReadyClient(billingFunction: BillingClient.() -> Unit) {
        billingClient.takeIf { isConnected }?.let {
            it.billingFunction()
        } ?: logger.debug("Connection to the BillingClient was lost")
    }

    fun subscribeOnPurchasesUpdates(purchasesUpdatedListener: PurchasesUpdatedListener) {
        this.purchasesUpdatedListener = purchasesUpdatedListener
    }

    fun setBillingClient(billingClient: BillingClient) {
        this.billingClient = billingClient
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        purchasesUpdatedListener?.onPurchasesUpdated(billingResult, purchases)
    }

    override fun onBillingServiceDisconnected() {
        logger.debug("onBillingServiceDisconnected() -> for $billingClient")
    }

    override fun onBillingSetupFinished(billingResult: BillingResult) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                logger.debug("onBillingSetupFinished() -> successfully for $billingClient.")
                connectionListener?.onBillingClientConnected()
            }
            BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED,
            BillingClient.BillingResponseCode.BILLING_UNAVAILABLE -> {
                logger.release("onBillingSetupFinished() -> with error: ${billingResult.getDescription()}")
                val error = BillingError(
                    billingResult.responseCode,
                    "Billing is not available on this device. ${billingResult.getDescription()}"
                )
                connectionListener?.onBillingClientUnavailable(error)
            }
            BillingClient.BillingResponseCode.DEVELOPER_ERROR -> {
                // Client is already in the process of connecting to billing service
            }
            else -> {
                logger.release("onBillingSetupFinished with error: ${billingResult.getDescription()}")
            }
        }
    }

    internal interface ConnectionListener {
        fun onBillingClientConnected()

        fun onBillingClientUnavailable(error: BillingError)
    }
}
