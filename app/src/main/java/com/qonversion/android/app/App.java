package com.qonversion.android.app;

import androidx.annotation.Nullable;
import androidx.multidex.MultiDexApplication;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.qonversion.android.sdk.Qonversion;
import com.qonversion.android.sdk.QonversionBillingBuilder;

import java.util.List;

public class App extends MultiDexApplication {
    @Override
    public void onCreate() {
        super.onCreate();
        Qonversion.initialize(
                this,
                BuildConfig.QONVERSION_API_KEY,
                "yourSideUserID",
                buildBilling(),
                true
        );
    }

    private QonversionBillingBuilder buildBilling() {
        return new QonversionBillingBuilder()
                .enablePendingPurchases()
                .setChildDirected(BillingClient.ChildDirected.CHILD_DIRECTED)
                .setListener(new PurchasesUpdatedListener() {
                    @Override
                    public void onPurchasesUpdated(BillingResult billingResult, @Nullable List<Purchase> purchases) {
                        // your purchases update logic
                    }
                });
    }
}
