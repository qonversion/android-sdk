package com.qonversion.android.sdk;

import android.app.Activity;
import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.AcknowledgePurchaseResponseListener;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ConsumeParams;
import com.android.billingclient.api.ConsumeResponseListener;
import com.android.billingclient.api.PriceChangeConfirmationListener;
import com.android.billingclient.api.PriceChangeFlowParams;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchaseHistoryResponseListener;
import com.android.billingclient.api.SkuDetails;
import com.android.billingclient.api.SkuDetailsParams;
import com.android.billingclient.api.SkuDetailsResponseListener;
import com.qonversion.android.sdk.billing.Billing;
import com.qonversion.android.sdk.logger.Logger;


import java.util.HashMap;
import java.util.List;

public class QonversionBilling implements Billing {

    private final BillingClient billingClient;
    private final HashMap<String, SkuDetails> details = new HashMap<>();
    private final Logger logger;
    private final boolean autoTracking;
    @Nullable
    private PurchaseReadyListener readyListener;

    private final PurchasesListener purchasesListener = new PurchasesListener() {
        @Override
        public void onPurchases(final List<Purchase> purchases) {
            for (Purchase p: purchases) {
                final SkuDetails skuDetails = details.get(p.getSku());
                if (readyListener != null) {
                    readyListener.onReady(p, skuDetails);
                }
            }
            logger.debug(
                    "onPurchases - purchases size - " + purchases.size() +
                    " details size - " + details.size()
            );
        }
    };
    public QonversionBilling(final Application context, final QonversionBillingBuilder builder, final Logger logger, boolean autoTracking) {
        builder.setUpdateCallback(purchasesListener);
        builder.setContext(context);
        builder.setLogger(logger);
        if (autoTracking) {
            builder.enableAutoTracking();
        }
        this.billingClient = builder.build();
        this.logger = logger;
        this.autoTracking = autoTracking;
    }

    @Override
    public BillingResult isFeatureSupported(String feature) {
        return billingClient.isFeatureSupported(feature);
    }

    @Override
    public boolean isReady() {
        return billingClient.isReady();
    }

    @Override
    public void startConnection(@NonNull BillingClientStateListener listener) {
        billingClient.startConnection(listener);
    }

    @Override
    public void endConnection() {
        billingClient.endConnection();
    }

    @Override
    public BillingResult launchBillingFlow(Activity activity, BillingFlowParams params) {
        return billingClient.launchBillingFlow(activity, params);
    }

    @Override
    public void launchPriceChangeConfirmationFlow(Activity activity, PriceChangeFlowParams params, @NonNull PriceChangeConfirmationListener listener) {
        billingClient.launchPriceChangeConfirmationFlow(activity, params, listener);
    }

    @Override
    public Purchase.PurchasesResult queryPurchases(String skuType) {
        return billingClient.queryPurchases(skuType);
    }

    @Override
    public void querySkuDetailsAsync(final SkuDetailsParams params, @NonNull final SkuDetailsResponseListener listener) {
            billingClient.querySkuDetailsAsync(params, new SkuDetailsResponseListener() {
                @Override
                public void onSkuDetailsResponse(BillingResult billingResult, List<SkuDetails> skuDetailsList) {
                    listener.onSkuDetailsResponse(billingResult, skuDetailsList);
                    if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && autoTracking) {
                        logger.debug("querySkuDetailsAsync - response code - " + billingResult.getResponseCode());
                        if (skuDetailsList != null && skuDetailsList.size() > 0) {
                            logger.debug("querySkuDetailsAsync - detail size - " + skuDetailsList.size());
                            for (SkuDetails d : skuDetailsList) {
                                details.put(d.getSku(), d);
                            }
                        }
                    }
                }
            });
    }

    @Override
    public void consumeAsync(ConsumeParams consumeParams, @NonNull ConsumeResponseListener listener) {
        billingClient.consumeAsync(consumeParams, listener);
    }

    @Override
    public void queryPurchaseHistoryAsync(String skuType, @NonNull PurchaseHistoryResponseListener listener) {
        billingClient.queryPurchaseHistoryAsync(skuType, listener);
    }

    @Override
    public void acknowledgePurchase(AcknowledgePurchaseParams params, AcknowledgePurchaseResponseListener listener) {
        billingClient.acknowledgePurchase(params, listener);
    }

    public void setReadyListener(@org.jetbrains.annotations.Nullable final PurchaseReadyListener readyListener) {
        this.readyListener = readyListener;
    }
}
