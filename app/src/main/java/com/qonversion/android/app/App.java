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
import com.qonversion.android.sdk.dto.LaunchMode;
import com.qonversion.android.sdk.dto.QAttributionSource;
import com.qonversion.android.sdk.dto.QUserProperties;
import com.qonversion.android.sdk.dto.Store;
import com.qonversion.android.sdk.dto.QonversionError;
import com.qonversion.android.sdk.listeners.QonversionLaunchCallback;
import com.qonversion.android.sdk.dto.QLaunchResult;

import org.jetbrains.annotations.NotNull;

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
                LaunchMode.Infrastructure,
                Store.GooglePlay
        ).build();
        Qonversion.initialize(qonversionConfig);
        Qonversion.getSharedInstance().launch(
                new QonversionLaunchCallback() {
                    @Override
                    public void onSuccess(@NotNull QLaunchResult launchResult) {
                    }

                    @Override
                    public void onError(@NotNull QonversionError error) {
                    }
                }
        );

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
