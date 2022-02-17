package com.qonversion.android.app;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;

import androidx.multidex.MultiDexApplication;

import com.appsflyer.AppsFlyerConversionListener;
import com.appsflyer.AppsFlyerLib;
import com.qonversion.android.sdk.QonversionConfig;
import com.qonversion.android.sdk.dto.LaunchMode;
import com.qonversion.android.sdk.dto.LogLevel;
import com.qonversion.android.sdk.internal.exception.QonversionException;
import com.qonversion.android.sdk.old.AttributionSource;
import com.qonversion.android.sdk.old.QUserProperties;
import com.qonversion.android.sdk.old.Qonversion;
import com.qonversion.android.sdk.old.QonversionError;
import com.qonversion.android.sdk.old.QonversionLaunchCallback;
import com.qonversion.android.sdk.old.dto.QLaunchResult;

import org.jetbrains.annotations.NotNull;

import java.util.Map;

import kotlin.Unit;

public class App extends MultiDexApplication {
    public static final String CHANNEL_ID = "qonversion";

    @Override
    public void onCreate() {
        super.onCreate();
        //  You can set the flag to distinguish sandbox and production users.
        //  Don't use it in production

        try {
            final QonversionConfig config = new QonversionConfig.Builder(
                    this,
                    "PV77YHL7qnGvsdmpTs7gimsxUvY-Znl2",
                    LaunchMode.InfrastructureMode
            )
                    .setLogLevel(LogLevel.Verbose)
                    .setEntitlementsUpdateListener(entitlements -> {
                        // Handle entitlements update
                    })
                    .build();
            final com.qonversion.android.sdk.Qonversion q = com.qonversion.android.sdk.Qonversion.initialize(config);
            q.getUserInfo(
                user -> Unit.INSTANCE
            ,
                error -> Unit.INSTANCE
            );
        } catch (QonversionException exception) {
            // Handle configuration exception
        }
        Qonversion.setDebugMode();
        Qonversion.launch(
                this,
                "PV77YHL7qnGvsdmpTs7gimsxUvY-Znl2",
                false,
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
                Qonversion.setProperty(QUserProperties.AppsFlyerUserId, AppsFlyerLib.getInstance().getAppsFlyerUID(App.this));
                Qonversion.attribution(conversionData, AttributionSource.AppsFlyer);
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
