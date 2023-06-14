package com.qonversion.android.app;

import android.os.Bundle;
import android.os.PersistableBundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.*;
import com.android.billingclient.api.SkuDetailsParams;
import com.android.billingclient.api.SkuDetailsResponseListener;
import com.qonversion.android.sdk.Qonversion;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ManualTrackingActivity extends AppCompatActivity {

    private static final String SKU_ID = "your_sku_id";

    private BillingClient client;

    @SuppressWarnings("deprecation")
    private final Map<String, SkuDetails> skuDetails = new HashMap<>();

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState, @Nullable PersistableBundle persistentState) {
        super.onCreate(savedInstanceState, persistentState);

        client = BillingClient
                .newBuilder(this)
                .enablePendingPurchases()
                .setListener((billingResult, list) -> {
                    if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                        if (list != null && !list.isEmpty()) {
                            Qonversion.getSharedInstance().syncPurchases();
                        }
                    }
                })
                .build();


        launchBilling();

    }

    private void launchBilling() {
        client.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(@NonNull BillingResult billingResult) {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {

                    @SuppressWarnings("deprecation")
                    final SkuDetailsParams params = SkuDetailsParams
                            .newBuilder()
                            .setSkusList(Collections.singletonList(SKU_ID))
                            .setType(BillingClient.SkuType.INAPP)
                            .build();

                    //noinspection deprecation
                    client.querySkuDetailsAsync(params, new SkuDetailsResponseListener() {
                        @Override
                        public void onSkuDetailsResponse(@NonNull BillingResult billingResult, List<SkuDetails> list) {
                            if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                                if (!list.isEmpty()) {
                                    skuDetails.put(SKU_ID, list.get(0));
                                }
                                launchBillingFlow();
                            }
                        }
                    });
                }
            }

            @Override
            public void onBillingServiceDisconnected() {
                // ignore in example
            }
        });

    }

    private void launchBillingFlow() {
        @SuppressWarnings("deprecation")
        final BillingFlowParams params = BillingFlowParams
                .newBuilder()
                .setSkuDetails(Objects.requireNonNull(skuDetails.get(SKU_ID)))
                .build();
        client.launchBillingFlow(this, params);
    }
}
