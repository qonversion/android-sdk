<p align="center">
 <a href="https://qonversion.io" target="_blank"><img width="360" height="150" src="https://qonversion.io/img/q_brand.svg"></a>
</p>

<p align="center">
     <a href="https://qonversion.io"><img width="660" src="https://qonversion.io/img/images/product-center.svg">
     </a>
</p>


<p>
Qonversion provides full in-app purchases infrastructure, so you do not need to build your own server for receipt validation.
</p>


<p>
Implement in-app subscriptions, validate user receipts, check subscription status, and provide access to your app features and content using our StoreKit wrapper and Google Play Billing wrapper.
</p>

Check the [documentation](https://docs.qonversion.io) to learn details on implementing and using Qonversion SDKs.

The latest release is available on [Bintray](https://dl.bintray.com/artemyglukhov/Qonversion).

[![Release](https://img.shields.io/github/release/qonversion/android-sdk.svg?style=flat)](https://github.com/qonversion/android-sdk/releases)
[![MIT License](http://img.shields.io/cocoapods/l/Qonversion.svg?style=flat)](http://qonversion.io)


## Product Center

<p align="center">
     <a href="https://qonversion.io"><img width="400" src="https://qonversion.io/img/images/product-center-scheme.svg">
     </a>
</p>

1. Application calls the purchase method to initialize Qonversion SDK.
2. Qonversion SDK communicates with StoreKit or Google Billing Client to make a purchase.
3. If a purchase is successful, the SDK sends a request to Qonversion API for server-to-server purchase validation. Qonversion server receives accurate information on the in-app purchase status and user entitlements.
4. SDK returns control to the application with a processing state.

## Automations
Qonversion Automation allows sending automated, personalized push notifications and in-app messages initiated by in-app purchase events. This feature is designed to increase your app's revenue and retention, provide cancellation insights, reduce subscriber churn, and improve your subscribers' user experience.

See more in the [documentation](https://documentation.qonversion.io/docs/automations)
![](https://qonversion.io/img/@2x/automation/in-app-constructor.gif)


## Analytics

Monitor your in-app revenue metrics. Understand your customers and make better decisions with precise subscription revenue data.

<p align="center">
     <a href="https://qonversion.io"><img width="90%" src="https://qonversion.io/img/screenshots/desktop/mobile_subscription_analytics.jpg">
     </a>
</p>

## Integrations

Share your iOS and Android in-app subscription data with your favorite platforms.


<p align="center">
     <a href="https://qonversion.io"><img width="500" src="https://qonversion.io/img/illustrations/pic-integration.svg">
     </a>
</p>


## License

Qonversion SDK is available under the MIT license.
