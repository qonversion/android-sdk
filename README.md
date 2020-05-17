<p align="center">
     <a href="https://qonversion.io"><img width="260" src="https://qonversion.io/img/brand.svg"></a>
</p>

<p align="center">
     <a href="https://qonversion.io"><img width="660" src="https://qonversion.io/img/illustrations/charts.svg"></a></p>

The latest release is available on [Bintray](https://dl.bintray.com/artemyglukhov/Qonversion).

### Instalation

1. Add qonversion to `dependencies` section in your app `build.gradle`

```kotlin
dependencies {
    ... 
    implementation "com.qonversion.android.sdk:sdk:1.0.2"
    ...
}
```

### Setup

Qonversion SDK can work in two modes depending on your goals:

1. Without autotracking purchases (manual mode). In this mode your should call SDK methods manualy, when your want to track purchase to Qonversion. 

2. With autotracking purchases (auto tracking mode). In this mode, you don’t have to worry about how your purchases tracks to Qonversion, all necessary work will take place inside SDK.

Next, in order will be considered the necessary steps to work in each of the modes


## 1. Manual mode

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

First of all your need setup and initialize Google Play Billing Library, following the [documentation](https://developer.android.com/google/play/billing/billing_library_overview) 

To track purchase data to the SDK you need to call the method `purchase`. So this method takes two parameters. All of these parameters is objects from [Google Play Billing Library](https://developer.android.com/google/play/billing/billing_library_overview) 

- details with type SkuDetails [docs](https://developer.android.com/reference/com/android/billingclient/api/SkuDetails).
- purchase with type Purchase [docs](https://developer.android.com/reference/com/android/billingclient/api/Purchase).

The best place to call method `purchase` it time when method [`onPurchasesUpdated`](https://developer.android.com/reference/com/android/billingclient/api/PurchasesUpdatedListener) of `BillingClient` will be called. 

#### For more information please see example app in this repo [ManualTrackingActivity](https://github.com/qonversion/android-sdk/blob/master/app/src/main/java/com/qonversion/android/app/ManualTrackingActivity.java) and [ManualTrackingActivityKt](https://github.com/qonversion/android-sdk/blob/master/app/src/main/java/com/qonversion/android/app/ManualTrackingActivityKt.kt) classes.

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


## 2. Auto Tracking mode

### 2.1 Initializing Qonversion SDK 

The SDK initialization should be called in your `Application` in the `onCreate` method following the steps:

#### Step 1. Add Qonversion SDK import

To import the Qonversion SDK, add the following code:
```java
import com.qonversion.android.sdk.Qonversion;
```
#### Step 2. Initialize QonversionBillingBuilder

Create an instance of `QonversionBillingBuilder`. It creation exactly like creation Android [BillingClient](https://developer.android.com/reference/com/android/billingclient/api/BillingClient.Builder) 

### Java

```java
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
```

### Kotlin

```kotlin
    private fun buildBilling(): QonversionBillingBuilder {
        return QonversionBillingBuilder()
            .enablePendingPurchases()
            .setChildDirected(BillingClient.ChildDirected.CHILD_DIRECTED)
            .setUnderAgeOfConsent(BillingClient.UnderAgeOfConsent.UNSPECIFIED)
            .setListener { billingResult, purchases ->
                // your purchases update logic
            }
    }
```
#### Step 3. Initializing Qonversion SDK.

To enable auto tracking mode put these parameters to Qonversion `initialize` method:

1. ApplicationContext
2. Your Qonversion API key.
3. Your side UserID
4. Instance of QonversionBillingBuilder from Step 2.
5. Autotracking - Boolean parameter put it to TRUE

### Java

```java
public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        QonversionBillingBuilder billingBuilder = buildBilling();
        Qonversion.initialize(this, BuildConfig.QONVERSION_API_KEY, "yourSideUserID", billingBuilder, true);
    }
```
### Kotlin

```kotlin
class App : Application() {

    override fun onCreate() {
        super.onCreate()
        val billingBuilder = buildBilling()
        Qonversion.initialize(this, BuildConfig.QONVERSION_API_KEY, "yourSideUserID", billingBuilder, true)
    }
}
```
### 2.2. Use Qonversion Billing instead Google BillingClient

For further work with SDK in auto tracking mode, your should use Qonversion Billing instance. 
It has exactly the same interface and methods as Google BillingClient. It’s just enough for you to call `Qonversion.instance?.billingClient` And to do what you want in the same way if you used the Google [BillingClient](https://developer.android.com/reference/com/android/billingclient/api/BillingClient.html). Below are 
simplified example of usage. 
#### For more information please see example app in this repo [MainActivity](https://github.com/qonversion/android-sdk/blob/master/app/src/main/java/com/qonversion/android/app/MainActivity.kt)

```kotlin

class MainActivity : AppCompatActivity() {

    private var billingClient : Billing? = null
    private val skuDetailsMap = mutableMapOf<String, SkuDetails>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        billingClient = Qonversion.instance?.billingClient
        billingClient?.startConnection(object : BillingClientStateListener {
            override fun onBillingServiceDisconnected() {
                // billing connection failed
            }

            override fun onBillingSetupFinished(billingResult: BillingResult?) {
                if (billingResult?.responseCode == BillingClient.BillingResponseCode.OK) {
                    launchBilling("your_purchase_id", "sku_type")
                }
            }
        })
    }

    private fun launchBilling(purchaseId: String, type: String) {
        var params = SkuDetailsParams.newBuilder()
            .setType(type)
            .setSkusList(listOf(purchaseId))
            .build()

        billingClient?.querySkuDetailsAsync(params, object: SkuDetailsResponseListener {
            override fun onSkuDetailsResponse(
                billingResult: BillingResult?,
                skuDetailsList: MutableList<SkuDetails>?
            ) {
                if (billingResult!!.responseCode == BillingClient.BillingResponseCode.OK) {
                    for (skuDetails in skuDetailsList!!) {
                         skuDetailsMap[skuDetails.sku] = skuDetails
                    }
                    launchBillingFlow(purchaseId)
                }
            }
        })
    }

    private fun launchBillingFlow(purchaseId: String) {
        val billingFlowParams = BillingFlowParams.newBuilder()
            .setSkuDetails(skuDetailsMap[purchaseId])
            .build()
        billingClient?.launchBillingFlow(this, billingFlowParams)
    }
}

```

## 3. Attribution

### 3.1 AppsFlyer

You need to have AppsFlyer SDK integrated in your app before starting with this integration. 
If you do not have Appsflyer integration yet, please use this [docs](https://support.appsflyer.com/hc/en-us/articles/207032126-Android-SDK-integration-for-developers#introduction)
When you receive the onConversionDataReceived callback you can use the `attribution` method 
for passing the attribution data dictionary:

### Java

```java
    @Override
    public void onConversionDataSuccess(final Map<String, Object> conversionData) {
          Qonversion.getInstance().attribution(
                  conversionData, 
                  AttributionSource.APPSFLYER, 
                  AppsFlyerLib.getInstance().getAppsFlyerUID(this)
                  );
    }
```
### Kotlin

```kotlin
    override fun onConversionDataSuccess(conversionData: Map<String, Any>) {
        Qonversion.instance?.attribution(
            conversionData,
            AttributionSource.APPSFLYER,
            AppsFlyerLib.getInstance().getAppsFlyerUID(applicationContext)
        )
    }
```



## Authors

Developed by Team of [Qonversion](https://qonversion.io), and written by [Artemy Glukhov](https://github.com/ArtemyGlukhov)

## License

Qonversion SDK is available under the MIT license.
