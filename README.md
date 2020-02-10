<p align="center">
     <a href="https://qonversion.io"><img width="260" src="https://qonversion.io/img/brand.svg"></a>
</p>

<p align="center">
     <a href="https://qonversion.io"><img width="660" src="https://qonversion.io/img/illustrations/charts.svg"></a></p>

The latest release is available on [Bintray](https://dl.bintray.com/artemyglukhov/Qonversion).

### Instalation

1. Add Qonversion SDK repository to the project/build.gradle file:
```kotlin
allprojects {
    repositories {
        maven { url 'https://dl.bintray.com/artemyglukhov/Qonversion'}
    }
}
```
2. Add qonversion to `dependencies` section in your app `build.gradle`

```kotlin
dependencies {
    ... 
    implementation "com.qonversion.android.sdk:sdk:0.2.2@aar"
    ...
}
```

### Setup

Qonversion SDK can work in two modes depending on your goals:

1. Without autotracking purchases (manual mode). In this mode your should call SDK methods manualy, when your want to track purchase to Qonversion. 

2. With autotracking purchases (automatic mode). In this mode, you don’t have to worry about how your purchases tracks to Qonversion, all necessary work will take place inside SDK.

Next, in order will be considered the necessary steps to work in each of the modes


### 1. Manual mode

### 1.1 Initializing Qonversion SDK 

To import the Qonversion SDK, add the following code:

```java
import com.qonversion.android.sdk.Qonversion;
```

The SDK initialization should be called in your `Application` in the `onCreate` method. 

### Java

```java
public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Qonversion.initialize(this, "projectKey", "yourSideUserID");
    }
}
```

### Kotlin

```kotlin
public class App : Application {
    override fun onCreate() {
        super.onCreate();
        Qonversion.initialize(this, "projectKey", "yourSideUserID");
    }
}
```

### 1.2 Usage Qonversion SDK in manual mode

First of all youn need setup and initialize Google Play Billing Library, following the [documentation](https://developer.android.com/google/play/billing/billing_library_overview) 

To track purchase data to the SDK you need to call the method `purchase`. So this method takes two parameters. All of these parameters is objects from [Google Play Billing Library](https://developer.android.com/google/play/billing/billing_library_overview) 

- details with type SkuDetails [docs](https://developer.android.com/reference/com/android/billingclient/api/SkuDetails).
- purchase with type Purchase [docs](https://developer.android.com/reference/com/android/billingclient/api/Purchase).

The best place to call method `purchase` it time when method [`onPurchasesUpdated`](https://developer.android.com/reference/com/android/billingclient/api/PurchasesUpdatedListener) of `BillingClient` will be called. 

#### For more information how it works please see example app in this repo [ManualTrackingActivity](https://github.com/qonversion/android-sdk/blob/master/app/src/main/java/com/qonversion/android/app/ManualTrackingActivity.java) and [ManualTrackingActivityKt](https://github.com/qonversion/android-sdk/blob/master/app/src/main/java/com/qonversion/android/app/ManualTrackingActivityKt.kt) classes.

### Java

```java
@Override
public void onCreate(@Nullable Bundle savedInstanceState, @Nullable PersistableBundle persistentState) {
     client = BillingClient
              .newBuilder(this)
              .enablePendingPurchases()
              .setListener(new PurchasesUpdatedListener() {
                    @Override
                    public void onPurchasesUpdated(BillingResult billingResult, @Nullable List<Purchase> list) {
                         if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                              if (list != null && !list.isEmpty()) {
                                   trackPurchase(skuDetails.get(SKU_ID), list.get(0));
                              }
                         }
                    }                
               })
               .build();
         
}

private void trackPurchase(@NonNull SkuDetails details, @NonNull Purchase purchase) {
    Qonversion.getInstance().purchase(details, purchase);
}
```

### Kotlin

```kotlin
    override fun onCreate(
        savedInstanceState: Bundle?,
        persistentState: PersistableBundle?
    ) {
        super.onCreate(savedInstanceState, persistentState)
        client = BillingClient
            .newBuilder(this)
            .enablePendingPurchases()
            .setListener { billingResult, list ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    if (list != null && list.isNotEmpty()) {
                        trackPurchase(
                            skuDetails[SKU_ID]!!,
                            list[0]
                        )
                    }
                }
            }
            .build()
        launchBilling()
    }
    
    private fun trackPurchase(
        details: SkuDetails,
        purchase: Purchase
    ) {
        Qonversion.instance!!.purchase(details, purchase)
    }
```



### 2. Advanced Usage (with auto tracking purchases)

In your `Application` in the `onCreate` method, setup the SDK like so.

1. Create instance of `QonversionBillingBuilder`. It looks like a standard `BillingClient` instantination. And put this as fourth param in `Qonversion.initialize` method

2. Put `autoTracking = TRUE` as fifth param in `Qonversion.initialize` method

```java
public class App extends MultiDexApplication {
    @Override
    public void onCreate() {
        super.onCreate();
        Qonversion.initialize(this, BuildConfig.QONVERSION_API_KEY, "yourSideUserID", buildBilling(), true);
    }

    private QonversionBillingBuilder buildBilling() {
        return new QonversionBillingBuilder()
                .enablePendingPurchases()
                .setChildDirected(BillingClient.ChildDirected.CHILD_DIRECTED)
                .setUnderAgeOfConsent(BillingClient.UnderAgeOfConsent.UNDER_AGE_OF_CONSENT)
                .setListener(new PurchasesUpdatedListener() {
                    @Override
                    public void onPurchasesUpdated(BillingResult billingResult, @Nullable List<Purchase> purchases) {
                        // your purchases update logic
                    }
                });
    }
}
```
And than in all places in your code use `Qonversion.instance?.billingClient` instead standard Google `BillingClient`. It has own type `Billing`, but it's no problem. It is one 100% match with standart Google `BillingClient` API.

```java
import com.qonversion.android.sdk.billing.Billing

// ...
private var billingClient : Billing? 

// ...
billingClient = Qonversion.instance?.billingClient

billingClient?.startConnection(...)
billingClient?.launchBillingFlow(...)
// etc

```

## Authors

Developed by Team of [Qonversion](https://qonversion.io), and written by [Artemy Glukhov](https://github.com/ArtemyGlukhov)

## License

Qonversion SDK is available under the MIT license.
