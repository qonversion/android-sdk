package io.qonversion.sample;

import static io.qonversion.sample.UtilsKt.getApiUrl;
import static io.qonversion.sample.UtilsKt.getProjectKey;

import androidx.multidex.MultiDexApplication;

import com.appsflyer.AppsFlyerConversionListener;
import com.appsflyer.AppsFlyerLib;
import com.google.firebase.FirebaseApp;
import com.qonversion.android.sdk.Qonversion;
import com.qonversion.android.sdk.QonversionConfig;
import com.qonversion.android.sdk.dto.QAttributionProvider;
import com.qonversion.android.sdk.dto.QEnvironment;
import com.qonversion.android.sdk.dto.QLaunchMode;
import com.qonversion.android.sdk.dto.properties.QUserPropertyKey;

import java.util.Map;

import io.qonversion.nocodes.NoCodes;
import io.qonversion.nocodes.NoCodesConfig;

public class App extends MultiDexApplication {
    private static final String DEFAULT_PROJECT_KEY = "PV77YHL7qnGvsdmpTs7gimsxUvY-Znl2";

    @Override
    public void onCreate() {
        super.onCreate();

        FirebaseApp.initializeApp(this);

        String projectKey = getProjectKey(this, DEFAULT_PROJECT_KEY);
        String apiUrl = getApiUrl(this);

        QonversionConfig.Builder qonversionConfigBuilder = new QonversionConfig.Builder(
                this,
                projectKey,
                QLaunchMode.SubscriptionManagement
        ).setEnvironment(QEnvironment.Sandbox);

        NoCodesConfig.Builder noCodesConfigBuilder = new NoCodesConfig.Builder(
                this,
                projectKey
        ).setCustomFallbackFileName("fallbacks/nocodes_fallbacks.json");

        if (apiUrl != null) {
            qonversionConfigBuilder.setProxyURL(apiUrl);
            noCodesConfigBuilder.setProxyURL(apiUrl);
        }

        Qonversion.initialize(qonversionConfigBuilder.build());
        NoCodes.initialize(noCodesConfigBuilder.build());

        AppsFlyerConversionListener conversionListener = new AppsFlyerConversionListener() {
            @Override
            public void onConversionDataSuccess(final Map<String, Object> conversionData) {
                Qonversion.getSharedInstance().setUserProperty(
                        QUserPropertyKey.AppsFlyerUserId,
                        AppsFlyerLib.getInstance().getAppsFlyerUID(App.this)
                );
                Qonversion.getSharedInstance().attribution(conversionData, QAttributionProvider.AppsFlyer);
            }

            @Override
            public void onConversionDataFail(String errorMessage) {}

            @Override
            public void onAppOpenAttribution(Map<String, String> conversionData) {}

            @Override
            public void onAttributionFailure(String errorMessage) {}
        };

        AppsFlyerLib.getInstance().init("afDevKey", conversionListener, this);
        AppsFlyerLib.getInstance().setDebugLog(true);
        AppsFlyerLib.getInstance().start(this);
    }
}
