package com.qonversion.android.sdk;

import android.app.Application;
import android.content.Context;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.qonversion.android.sdk.logger.Logger;

public class QonversionBillingBuilder {

    private Context mContext;
    private boolean mEnablePendingPurchases;
    private boolean mEnableAutoTracking = false;
    private PurchasesUpdatedListener mListener;
    private PurchasesListener updateCallback;
    private Logger logger;

    public QonversionBillingBuilder() {

    }

    public QonversionBillingBuilder setListener(PurchasesUpdatedListener listener) {
        mListener = listener;
        return this;
    }

    public QonversionBillingBuilder enablePendingPurchases() {
        mEnablePendingPurchases = true;
        return this;
    }

    QonversionBillingBuilder enableAutoTracking() {
        mEnableAutoTracking = true;
        return this;
    }

    QonversionBillingBuilder setUpdateCallback(PurchasesListener c) {
        updateCallback = c;
        return this;
    }

    QonversionBillingBuilder setContext(Application context) {
        mContext = context;
        return this;
    }

    QonversionBillingBuilder setLogger(Logger l) {
        logger = l;
        return this;
    }


    protected BillingClient build() {
        if (mContext == null) {
            throw new IllegalArgumentException("Please provide a valid Context.");
        }
        if (mListener == null) {
            throw new IllegalArgumentException(
                    "Please provide a valid listener for purchases updates.");
        }
        if (!mEnablePendingPurchases) {
            throw new IllegalArgumentException(
                    "Support for pending purchases must be enabled. Enable "
                            + "this by calling 'enablePendingPurchases()' on BillingClientBuilder.");
        }

        final PurchasesUpdatedListener listener = mEnableAutoTracking
                ? new QonversionUpdatedListener(mListener, updateCallback, logger)
                : mListener;

        return BillingClient
                .newBuilder(mContext)
                .enablePendingPurchases()
                .setListener(listener)
                .build();
    }
}
