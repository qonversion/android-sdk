package com.qonversion.android.sdk;

import androidx.annotation.Nullable;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.qonversion.android.sdk.logger.Logger;

import java.util.List;

class QonversionUpdatedListener implements PurchasesUpdatedListener {

    private final PurchasesUpdatedListener original;
    private final PurchasesListener callback;
    private final Logger logger;

    QonversionUpdatedListener(PurchasesUpdatedListener original, PurchasesListener callback, Logger logger) {
        this.original = original;
        this.callback = callback;
        this.logger = logger;
    }

    @Override
    public void onPurchasesUpdated(BillingResult billingResult, @Nullable List<Purchase> purchases) {
        original.onPurchasesUpdated(billingResult, purchases);
        logger.log("onPurchasesUpdated - response code - " + billingResult.getResponseCode());
        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
            if (purchases != null && purchases.size() > 0) {
                callback.onPurchases(purchases);
                logger.log("onPurchasesUpdated - purchases size - " + purchases.size());
            }
        }
    }
}
