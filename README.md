<p align="center">
     <a href="https://qonversion.io"><img width="260" src="https://qonversion.io/img/brand.svg"></a>
</p>

<p align="center">
     <a href="https://qonversion.io"><img width="660" src="https://qonversion.io/img/illustrations/charts.svg"></a></p>

The latest release is available on [Bintray](https://dl.bintray.com/artemyglukhov/Qonversion).

### Instalation

1. Add maven `url` to `allprojects` in your project `build.gradle`
```kotlin
allprojects {
    repositories {
        jcenter()
        maven {
            url 'https://dl.bintray.com/artemyglukhov/Qonversion'
        }
        google()
    }
}
```
2. Add qonversion to `dependencies` section in your app `build.gradle`

```kotlin
    implementation "com.qonversion.android.sdk:sdk:0.2.2@aar"
```

### 1. Basic Usage (without auto tracking purchases)

In your `Application` in the `onCreate` method, setup the SDK like so:

```java
public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        Qonversion.initialize(this, "projectKey", "yourSideUserID");
    }
}
```

In your `BillingClient` listener, when `onPurchasesUpdated` callback has been called, track your purchase to Qonversion SDK like this:

### Kotlin

```kotlin
    private fun purchase(details: SkuDetails, purchase: Purchase) {
        Qonversion.instance?.purchase(details, purchase)
    }
```

### Java

```java
    private void purchase(@NonNull SkuDetails details, @NonNull Purchase purchase) {
        Qonversion.getInstance().purchase(details, purchase);
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
