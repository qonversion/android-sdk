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
import com.android.billingclient.api.PendingPurchasesParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.qonversion.android.sdk.Qonversion;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class ManualTrackingActivity extends AppCompatActivity {

    private static final String PRODUCT_ID = "your_product_id";

    private BillingClient client;

    private final Map<String, ProductDetails> productDetails = new HashMap<>();

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState, @Nullable PersistableBundle persistentState) {
        super.onCreate(savedInstanceState, persistentState);

        client = BillingClient
                .newBuilder(this)
                .enablePendingPurchases(
                        PendingPurchasesParams.newBuilder()
                                .enableOneTimeProducts()
                                .enablePrepaidPlans()
                                .build()
                )
                .setListener((billingResult, purchases) -> {
                    if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                        if (purchases != null && !purchases.isEmpty()) {
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

                    final QueryProductDetailsParams.Product product = QueryProductDetailsParams.Product
                            .newBuilder()
                            .setProductId(PRODUCT_ID)
                            .setProductType(BillingClient.ProductType.INAPP)
                            .build();

                    final QueryProductDetailsParams params = QueryProductDetailsParams
                            .newBuilder()
                            .setProductList(Collections.singletonList(product))
                            .build();

                    client.queryProductDetailsAsync(params, (queryBillingResult, details) -> {
                        if (queryBillingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                            if (!details.isEmpty()) {
                                productDetails.put(PRODUCT_ID, details.get(0));
                            }
                            launchBillingFlow();
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
        final BillingFlowParams.ProductDetailsParams productDetailsParams = BillingFlowParams.ProductDetailsParams
                .newBuilder()
                .setProductDetails(Objects.requireNonNull(productDetails.get(PRODUCT_ID)))
                .build();

        final BillingFlowParams params = BillingFlowParams
                .newBuilder()
                .setProductDetailsParamsList(Collections.singletonList(productDetailsParams))
                .build();

        client.launchBillingFlow(this, params);
    }
}
