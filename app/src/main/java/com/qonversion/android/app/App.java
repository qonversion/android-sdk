package com.qonversion.android.app;

import androidx.multidex.MultiDexApplication;

import com.appsflyer.AppsFlyerConversionListener;
import com.appsflyer.AppsFlyerLib;
import com.qonversion.android.sdk.AttributionSource;
import com.qonversion.android.sdk.QUserProperties;
import com.qonversion.android.sdk.Qonversion;
import com.qonversion.android.sdk.QonversionError;
import com.qonversion.android.sdk.QonversionLaunchCallback;
import com.qonversion.android.sdk.dto.QLaunchResult;

import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class App extends MultiDexApplication {

    @Override
    public void onCreate() {
        super.onCreate();

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
        Qonversion.setProperty(QUserProperties.AppsFlyerUserId, AppsFlyerLib.getInstance().getAppsFlyerUID(this));
        AppsFlyerConversionListener conversionListener = new AppsFlyerConversionListener() {

            @Override
            public void onConversionDataSuccess(final Map<String, Object> conversionData) {
                Qonversion.attribution(conversionData, AttributionSource.APPSFLYER);
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
    }
}
