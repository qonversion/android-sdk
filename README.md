<h1 align="center">
    Qonversion
</h1>

Qonversion - In-app subscription monetization: implement subscriptions and grow your app’s revenue with A/B experiments 

* In-app subscription management SDK
* API and webhooks to make your subscription data available where you need it
* Seamless Stripe integration to enable cross-platform access management
* Subscribers CRM with user-level transactions
* Instant access to real-time subscription analytics
* Built-in A/B experiments for subscription business model

<p align="center">
     <a href="https://qonversion.io"><img width="90%" src="https://qcdn3.sfo3.digitaloceanspaces.com/github/qonversion_platform.png">
     </a>
</p>

[![Release](https://img.shields.io/github/release/qonversion/android-sdk.svg?style=flat)](https://github.com/qonversion/android-sdk/releases)
[![MIT License](http://img.shields.io/cocoapods/l/Qonversion.svg?style=flat)](https://qonversion.io)


## In-App Subscription Implementation & Management

<p align="center">
     <a href="https://documentation.qonversion.io/docs/integrations-overview"><img width="90%" src="https://user-images.githubusercontent.com/13959241/161107203-8ef3ecee-86be-47a2-ac57-b21d3da19339.png">
     </a>
</p>

1. Qonversion SDK provides three simple methods to manage subscriptions:
	* Get in-app product details
	* Make purchases
	* Check subscription status to manage premium access
2. Qonversion communicates with Apple or Google platforms both through SDK and server-side to process native in-app payments and keep subscription statuses up to date.
3. You can use Qonversion webhooks and API in addition to SDK to get user-level data where you need it.

See the [quick start guide documentation](https://documentation.qonversion.io/docs/quickstart).

## Analytics

Qonversion provides advanced subscription analytics out-of-the-box. You can monitor real-time metrics from new users and trial-to-paid conversions to revenue, MRR, ARR, cohort retention and more. Understand your customers and make better decisions with precise subscription analytics.

<p align="center">
     <a href="https://documentation.qonversion.io/docs/analytics"><img width="90%" src="https://files.readme.io/9a4fdf6-Analytics.png">
     </a>
</p>


## A/B Experiments

Qonversion's A/B Experiments feature provides everything required to quickly launch paywall and other monetization experiments, analyze results and roll out winning versions without releasing a new app build. Qonversion A/B Experiments include:

* User segmentation by country, install date, app version, free/paying user
* Traffic allocation
* Advanced subscription analytics
* Visualization of A/B experiments results
* Statistical significance of the results
* Roll out winning versions without app release with remote config


<p align="center">
     <a href="https://documentation.qonversion.io/docs/subscription-ab-testing"><img width="90%" src="https://qcdn3.sfo3.digitaloceanspaces.com/github/ab_tests.png">
     </a>
</p>

See more details [here](https://documentation.qonversion.io/docs/paywall-experiments).

## Integrations

Send user-level subscription data to your favorite platforms.

* Amplitude
* Mixpanel
* Appsflyer
* Adjust
* Singular
* CleverTap
* [All other integrations here](qonversion.io/integrations)

<p align="center">
     <a href="https://documentation.qonversion.io/docs/integrations-overview"><img width="90%", src="https://qcdn3.sfo3.digitaloceanspaces.com/github/integrations.png">
     </a>
</p>

## Web2App

Web2App lets a user pay on your web checkout and unlock entitlements in the Android app. After payment, Qonversion emails the user a redemption link of the form:

```
https://screens.qonversion.io/r/{project_uid}/{token}
```

When the user taps it, Android opens your app via a verified [App Link](https://developer.android.com/training/app-links), and you forward the `Uri` to the SDK, which redeems the token and merges the purchase into the current user.

### 1. Register the App Link

Add an `<intent-filter>` to the activity that should receive the link. It **must** use `https` and `android:autoVerify="true"`, and the path **must** be scoped to your own `{project_uid}` (issued in Qonversion Connect Apps onboarding) so other merchants' apps can't intercept the token:

```xml
<activity
    android:name=".MainActivity"
    android:launchMode="singleTask"
    android:exported="true">

    <intent-filter android:autoVerify="true">
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data
            android:scheme="https"
            android:host="screens.qonversion.io"
            android:pathPattern="/r/YOUR_PROJECT_UID/.*" />
    </intent-filter>
</activity>
```

> Only `https` App Links are supported as an email-link transport. The SDK rejects any other scheme or host before any network call, because any installed app can claim a custom (`qonversion://`) scheme and hijack the token. Use `singleTask` (or `singleTop`) so re-taps arrive in `onNewIntent` instead of relaunching the activity.

### 2. Verify domain ownership (`assetlinks.json`)

`autoVerify` requires Qonversion to host a Digital Asset Links file at `https://screens.qonversion.io/.well-known/assetlinks.json` that pins your app's package name and signing-certificate SHA-256 fingerprint. Provide these to Qonversion during onboarding. You can generate the fingerprint with:

```bash
keytool -list -v -keystore your-release.keystore -alias your-alias
```

Until the file lists your app, Android shows a chooser dialog instead of opening your app directly.

### 3. Forward the link to the SDK

In the activity that owns the intent-filter, forward the incoming `Uri` from both `onCreate` and `onNewIntent`:

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    handleRedemptionIntent(intent)
}

override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    handleRedemptionIntent(intent)
}

private fun handleRedemptionIntent(intent: Intent?) {
    val uri = intent?.data ?: return
    if (uri.scheme != "https" || uri.pathSegments.firstOrNull() != "r") return

    Qonversion.shared.handleRedemptionLink(uri, object : QonversionRedemptionCallback {
        override fun onResult(result: RedemptionResult) {
            // Delivered once on the main thread.
            when (result) {
                RedemptionResult.Success -> { /* entitlement granted — refresh the UI */ }
                RedemptionResult.AlreadyConsumed,
                RedemptionResult.TokenExpired -> { /* offer the reissue dialog */ }
                RedemptionResult.InvalidToken -> { /* show an error */ }
                RedemptionResult.NetworkError,
                RedemptionResult.Retryable -> { /* offline / server asked to back off — retry later */ }
            }
        }
    })
}
```

On `RedemptionResult.Success` the entitlement has **already** been granted server-side (grant-first) and attached to the SDK user. The SDK does **not** call `identify`/merge — it only refreshes entitlements, so your next `checkEntitlements` reflects the new state. (You therefore do not need to add an `identify` call yourself.) Do **not** log or display the full redemption `Uri` — it carries a single-use token.

### 4. Let users request a new link (`presentReissueUI`)

If the original link is lost or expired, present the built-in dialog so the user can request a new redemption email. No host theme or layout resources are required:

```kotlin
Qonversion.shared.presentReissueUI(this) { success ->
    // success == true once the reissue email is sent.
}
```

## Why Qonversion?

* **No headaches with Apple's StoreKit & Google Billing.** Qonversion provides simple methods to handle Apple StoreKit & Google Billing purchase flow.
* **Receipt validation.** Qonversion validates user receipts with Apple and Google to provide 100% accurate purchase information and subscription statuses. It also prevents unauthorized access to the premium features of your app.
* **Track and increase your revenue.** Qonversion provides detailed real-time revenue analytics including cohort analysis, trial conversion rates, country segmentation, and much more.
* **Integrations with the leading mobile platforms.** Qonversion allows sending data to platforms like AppsFlyer, Adjust, Branch, Tenjin, Facebook Ads, Amplitude, Mixpanel, and many others.
* **Change promoted in-app products.** Change promoted in-app products anytime without app releases.
* **A/B test** and identify winning in-app purchases, subscriptions or paywals.
* **Cross-device and cross-platform access management.** If you provide user authorization in your app, you can easily set Qonversion to provide premium access to authorized users across devices and operating systems.
* **SDK caches the data.** Qonversion SDK caches purchase data including in-app products and entitlements, so the user experience is not affected even with the slow or interrupting network connection.
* **Webhooks.** You can easily send all of the data to your server with Qonversion webhooks.
* **Customer support.** You can always reach out to our customer support and get the help required.

Convinced? Let's go!

## Documentation

Check the [full documentation](https://documentation.qonversion.io/docs/quickstart) to learn about implementation details and available features.

#### Help us improve the documentation

Whether you’re a core user or trying it out for the first time, you can make a valuable contribution to Qonversion by improving the documentation. Help us by:

* sending us feedback about something you thought was confusing or simply missing
* sending us a pull request via GitHub
* suggesting better wording or ways of explaining certain topics in the [Qonversion documentation](http://documentation.qonversion.io). Use `SUGGEST EDITS` button in the top right corner.

## Contributing

Contributions are what make the open source community such an amazing place to learn, inspire, and create. Any contributions you make are **greatly appreciated**.

1. Fork the Project
2. Create your Feature Branch (`git checkout -b feature/SuperFeature`)
3. Commit your Changes. Use small commits with separate logic. (`git commit -m 'Add some super feature'`)
4. Push to the Branch (`git push origin feature/SuperFeature`)
5. Open a Pull Request


## Have a question?

Contact us via [issues on GitHub](https://github.com/qonversion/android-sdk/issues) or [ask a question](https://documentation.qonversion.io/discuss-new) on the site.

## License

Qonversion SDK is available under the MIT license.