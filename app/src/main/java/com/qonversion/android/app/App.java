package com.qonversion.android.app;

import androidx.annotation.Nullable;
import androidx.multidex.MultiDexApplication;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.appsflyer.AppsFlyerConversionListener;
import com.appsflyer.AppsFlyerLib;
import com.qonversion.android.sdk.AttributionSource;
import com.qonversion.android.sdk.Qonversion;
import com.qonversion.android.sdk.QonversionBillingBuilder;
import com.qonversion.android.sdk.QonversionCallback;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

public class App extends MultiDexApplication {

    @Override
    public void onCreate() {
        super.onCreate();
        Qonversion.initialize(
                this,
                BuildConfig.QONVERSION_API_KEY,
                "yourSideUserID",
                buildBilling(),
                true,
                new QonversionCallback() {
                    @Override
                    public void onSuccess(@NotNull String uid) {

                    }

                    @Override
                    public void onError(@NotNull Throwable t) {

                    }
                }
        );

        AppsFlyerConversionListener conversionListener = new AppsFlyerConversionListener() {

            @Override
            public void onConversionDataSuccess(final Map<String, Object> conversionData) {
                Qonversion.getInstance().attribution(conversionData, AttributionSource.APPSFLYER, AppsFlyerLib.getInstance().getAppsFlyerUID(App.this));
            }

            @Override
            public void onConversionDataFail(String errorMessage) {

            }

            @Override
            public void onAppOpenAttribution(Map<String, String> conversionData) {

            }

            @Override
            public void onAttributionFailure(String errorMessage) {

            }
        };

        AppsFlyerLib.getInstance().init(BuildConfig.AF_DEV_KEY, conversionListener, this);
        AppsFlyerLib.getInstance().setDebugLog(true);
        AppsFlyerLib.getInstance().startTracking(this);
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
