package com.qonversion.android.app;

import android.app.Application;

import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.SkuDetails;
import com.qonversion.android.sdk.Qonversion;

public class JavaClient {
    void test(Application application, SkuDetails details, Purchase purchase) {
        Qonversion.purchase(details, purchase);
    }
}
