package com.qonversion.android.sdk;

import com.android.billingclient.api.Purchase;

import java.util.List;

public interface PurchasesListener {
    void onPurchases(List<Purchase> purchases);
}
