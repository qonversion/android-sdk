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
    implementation "com.qonversion.android.sdk:sdk:0.1.0@aar"
```

### Usage

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


## Authors

Developed by Team of [Qonversion](https://qonversion.io), and written by [Artemy Glukhov](https://github.com/ArtemyGlukhov)

## License

Qonversion SDK is available under the MIT license.
