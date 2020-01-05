package com.qonversion.android.sdk.billing;

import android.app.Activity;

import androidx.annotation.NonNull;

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
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.RewardLoadParams;
import com.android.billingclient.api.RewardResponseListener;
import com.android.billingclient.api.SkuDetails;
import com.android.billingclient.api.SkuDetailsParams;
import com.android.billingclient.api.SkuDetailsResponseListener;
import com.qonversion.android.sdk.QonversionBilling;


public interface Billing {

    /**
     * Initiate the billing flow for an in-app purchase or subscription.
     *
     * <p>It will show the Google Play purchase screen. The result will be delivered via the {@link
     * PurchasesUpdatedListener} interface implementation reported to the {@link QonversionBilling}
     * constructor.
     * @param activity An activity reference from which the billing flow will be launched.
     * @param params Params specific to the request {@link BillingFlowParams}).
     * @return int The response code ({@link BillingClient.BillingResponseCode}) of launch flow operation.
     */
    BillingResult launchBillingFlow(Activity activity, BillingFlowParams params);

    /**
     * Initiate a flow to confirm the change of price for an item subscribed by the user.
     *
     * <p>When the price of a user subscribed item has changed, launch this flow to take users to
     * a screen with price change information. User can confirm the new price or cancel the flow.
     *
     * @param activity An activity reference from which the billing flow will be launched.
     * @param params Params specific to the request {@link PriceChangeFlowParams}).
     * @param listener Implement it to get the result of your price change flow.
     */
    void launchPriceChangeConfirmationFlow(Activity activity, PriceChangeFlowParams params, @NonNull PriceChangeConfirmationListener listener);

    /**
     * Get purchases details for all the items bought within your app. This method uses a cache of
     * Google Play Store app without initiating a network request.
     *
     * <p>Note: It's recommended for security purposes to go through purchases verification on your
     * backend (if you have one) by calling one of the following APIs:
     * https://developers.google.com/android-publisher/api-ref/purchases/products/get
     * https://developers.google.com/android-publisher/api-ref/purchases/subscriptions/get
     *
     * @param skuType The type of SKU, either "inapp" or "subs" as in {@link BillingClient.SkuType}.
     * @return PurchasesResult The {@link Purchase.PurchasesResult} containing the list of purchases and the
     *     response code ({@link BillingClient.BillingResponseCode}
     */
    Purchase.PurchasesResult queryPurchases(String skuType);

    /**
     * Perform a network query to get SKU details and return the result asynchronously.
     *
     * @param params Params specific to this query request {@link SkuDetailsParams}.
     * @param listener Implement it to get the result of your query operation returned asynchronously
     *     through the callback with the {@link BillingClient.BillingResponseCode} and the list of {@link
     *     SkuDetails}.
     */
    void querySkuDetailsAsync(SkuDetailsParams params, @NonNull SkuDetailsResponseListener listener);

    /**
     * Consumes a given in-app product. Consuming can only be done on an item that's owned, and as a
     * result of consumption, the user will no longer own it.
     *
     * <p>Consumption is done asynchronously and the listener receives the callback specified upon
     * completion.
     *
     * <p><b>Warning!</b> All purchases require acknowledgement. Failure to acknowledge a purchase
     * will result in that purchase being refunded. For one-time products ensure you are using this
     * method which acts as an implicit acknowledgement or you can explicitly acknowledge the purchase
     * via {@link #acknowledgePurchase). For subscriptions use {@link #acknowledgePurchase).
     * Please refer to
     * https://developer.android.com/google/play/billing/billing_library_overview#acknowledge for more
     * details.
     *
     * @param consumeParams Params specific to consume purchase.
     * @param listener Implement it to get the result of your consume operation returned
     *     asynchronously through the callback with token and {@link BillingClient.BillingResponseCode } parameters.
     */
    void consumeAsync(ConsumeParams consumeParams, @NonNull ConsumeResponseListener listener);

    /**
     * Returns the most recent purchase made by the user for each SKU, even if that purchase is
     * expired, canceled, or consumed.
     *
     * @param skuType The type of SKU, either "inapp" or "subs" as in {@link BillingClient.SkuType}.
     * @param listener Implement it to get the result of your query returned asynchronously through
     *     the callback with a {@link Purchase.PurchasesResult} parameter.
     */
    void queryPurchaseHistoryAsync(String skuType, @NonNull PurchaseHistoryResponseListener listener);

    /**
     * Loads a rewarded sku in the background and returns the result asynchronously.
     *
     * <p>If the rewarded sku is available, the response will be BILLING_RESULT_OK. Otherwise the
     * response will be ITEM_UNAVAILABLE. There is no guarantee that a rewarded sku will always be
     * available. After a successful response, only then should the offer be given to a user to obtain
     * a rewarded item and call launchBillingFlow.
     *
     * @param params Params specific to this load request {@link RewardLoadParams}
     * @param listener Implement it to get the result of the load operation returned asynchronously
     *     through the callback with the {@link BillingClient.BillingResponseCode}
     */
    void loadRewardedSku(RewardLoadParams params, @NonNull RewardResponseListener listener);

    /**
     * Acknowledge in-app purchases.
     *
     * <p>Developers are required to acknowledge that they have granted entitlement for all in-app
     * purchases for their application.
     *
     * <p><b>Warning!</b> All purchases require acknowledgement. Failure to acknowledge a purchase
     * will result in that purchase being refunded. For one-time products ensure you are using
     * {@link #consumeAsync) which acts as an implicit acknowledgement or you can explicitly
     * acknowledge the purchase via this method. For subscriptions use {@link #acknowledgePurchase).
     * Please refer to
     * https://developer.android.com/google/play/billing/billing_library_overview#acknowledge for more
     * details.
     *
     * @param params Params specific to this acknowledge purchase request {@link
     *     AcknowledgePurchaseParams}
     * @param listener Implement it to get the result of the acknowledge operation returned
     *     asynchronously through the callback with the {@link BillingClient.BillingResponseCode }
     */
    void acknowledgePurchase(AcknowledgePurchaseParams params, AcknowledgePurchaseResponseListener listener);

    /**
     * Check if specified feature or capability is supported by the Play Store.
     *
     * @param feature One of {@link BillingClient.FeatureType} constants.
     * @return BILLING_RESULT_OK if feature is supported and corresponding error code otherwise.
     */
    BillingResult isFeatureSupported(String feature);

    /**
     * Checks if the client is currently connected to the service, so that requests to other methods
     * will succeed.
     * <p>Returns true if the client is currently connected to the service, false otherwise.
     * <p>Note: It also means that INAPP items are supported for purchasing, queries and all other
     * actions.
     */
    boolean isReady();

    /**
     * Starts up BillingClient setup process asynchronously. You will be notified through the {@link
     * BillingClientStateListener} listener when the setup process is complete.
     *
     * @param listener The listener to notify when the setup process is complete.
     */
    void startConnection(@NonNull BillingClientStateListener listener);

    /**
     * Close the connection and release all held resources such as service connections.
     *
     * <p>Call this method once you are done with this BillingClient reference.
     */
    void endConnection();

}
