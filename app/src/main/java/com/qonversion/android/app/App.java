package com.qonversion.android.app;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;

import androidx.multidex.MultiDexApplication;

import com.appsflyer.AppsFlyerConversionListener;
import com.appsflyer.AppsFlyerLib;
import com.google.firebase.*;
import com.qonversion.android.sdk.Qonversion;
import com.qonversion.android.sdk.QonversionConfig;
import com.qonversion.android.sdk.automations.Automations;
import com.qonversion.android.sdk.dto.QEnvironment;
import com.qonversion.android.sdk.dto.QLaunchMode;
import com.qonversion.android.sdk.dto.QAttributionSource;
import com.qonversion.android.sdk.dto.QUserProperties;

import java.util.Map;

public class App extends MultiDexApplication {
    public static final String CHANNEL_ID = "qonversion";

    @Override
    public void onCreate() {
        super.onCreate();

        FirebaseApp.initializeApp(this);

        final QonversionConfig qonversionConfig = new QonversionConfig.Builder(
                this,
                "PV77YHL7qnGvsdmpTs7gimsxUvY-Znl2",
                QLaunchMode.Infrastructure
        )
                .setEnvironment(QEnvironment.Sandbox)
                .build();
        Qonversion.initialize(qonversionConfig);
        Automations.initialize(); // Initialize if you use Automations.

        AppsFlyerConversionListener conversionListener = new AppsFlyerConversionListener() {

            @Override
            public void onConversionDataSuccess(final Map<String, Object> conversionData) {
                Qonversion.getSharedInstance().setProperty(
                        QUserProperties.AppsFlyerUserId,
                        AppsFlyerLib.getInstance().getAppsFlyerUID(App.this)
                );
                Qonversion.getSharedInstance().attribution(conversionData, QAttributionSource.AppsFlyer);
            }

            @Override
            public void onConversionDataFail(String errorMessage) {

            }

            @Override
            public void onAppOpenAttribution(Map<String, String> conversionData) {

            }

            @Override
            public void onAttributionFailure(String errorMessage) {

            }
        };

        AppsFlyerLib.getInstance().init("afDevKey", conversionListener, this);
        AppsFlyerLib.getInstance().setDebugLog(true);
        AppsFlyerLib.getInstance().startTracking(this);

        createNotificationChannel();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.channel_name),
                    NotificationManager.IMPORTANCE_HIGH
            );

            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }
}
