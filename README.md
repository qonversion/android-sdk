<p align="center">
     <a href="https://qonversion.io"><img width="260" src="https://qonversion.io/img/brand.svg"></a>
</p>

<p align="center">
     <a href="https://qonversion.io"><img width="660" src="https://qonversion.io/img/illustrations/charts.svg"></a></p>

### Instalation

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

### Usage

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


## Authors

Developed by Team of [Qonversion](https://qonversion.io), and written by [Artemy Glukhov](https://github.com/ArtemyGlukhov)

## License

Qonversion SDK is available under the MIT license.
