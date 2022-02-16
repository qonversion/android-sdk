package com.qonversion.android.sdk.internal.utils

import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchaseHistoryRecord

internal val Purchase.sku: String? get() = skus.firstOrNull()

internal val PurchaseHistoryRecord.sku: String? get() = skus.firstOrNull()

internal val Context.isDebuggable get() = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

internal val Context.application get() = applicationContext as Application
