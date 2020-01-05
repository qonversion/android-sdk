package com.qonversion.android.sdk;

import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.SkuDetails;

public interface PurchaseReadyListener {
    void onReady(Purchase purchase, SkuDetails details);
}
