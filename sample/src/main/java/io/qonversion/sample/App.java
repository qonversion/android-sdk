package io.qonversion.sample;

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
    @Override
    public void onCreate() {
        super.onCreate();

        FirebaseApp.initializeApp(this);

        final QonversionConfig qonversionConfig = new QonversionConfig.Builder(
                this,
                "PV77YHL7qnGvsdmpTs7gimsxUvY-Znl2",
                QLaunchMode.SubscriptionManagement
        )
                .setEnvironment(QEnvironment.Sandbox)
                .build();

        Qonversion.initialize(qonversionConfig);

        final NoCodesConfig noCodesConfig = new NoCodesConfig.Builder(
                this,
                "PV77YHL7qnGvsdmpTs7gimsxUvY-Znl2"
        ).build();
        NoCodes.initialize(noCodesConfig);

        Qonversion.getSharedInstance().syncHistoricalData();

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
