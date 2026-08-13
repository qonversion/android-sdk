package io.qonversion.sample;

import static io.qonversion.sample.UtilsKt.getApiUrl;
import static io.qonversion.sample.UtilsKt.getProjectKey;

import androidx.multidex.MultiDexApplication;

import com.qonversion.android.sdk.Qonversion;
import com.qonversion.android.sdk.QonversionConfig;
import com.qonversion.android.sdk.dto.QEnvironment;
import com.qonversion.android.sdk.dto.QLaunchMode;
import com.qonversion.android.sdk.dto.remoteconfig.QRemoteConfigV2Config;

import io.qonversion.nocodes.NoCodes;
import io.qonversion.nocodes.NoCodesConfig;

public class App extends MultiDexApplication {
    // The RC v2 playground project. The RemoteConfigV2Fragment demo only works against the project
    // on the local gateway's RC v2 allowlist, so that project is the sample's default. The
    // configuration dialog on the Home screen still overrides it at runtime without a rebuild.
    private static final String DEFAULT_PROJECT_KEY = UtilsKt.RC_V2_PLAYGROUND_PROJECT_KEY;

    @Override
    public void onCreate() {
        super.onCreate();

        String projectKey = getProjectKey(this, DEFAULT_PROJECT_KEY);
        String apiUrl = getApiUrl(this);
        // The sample's default project belongs to the local RC v2 playground. Keeping the
        // legacy/identity API on production while snapshots use the local gateway creates a
        // split-brain identity scope: identify() mutates a production user and the local RC
        // session continues to resolve the anonymous uid. An explicitly configured URL still
        // wins, and a non-playground project keeps the SDK's normal production default.
        String effectiveApiUrl = apiUrl != null
                ? apiUrl
                : (DEFAULT_PROJECT_KEY.equals(projectKey)
                    ? UtilsKt.RC_V2_PLAYGROUND_BASE_URL + "/"
                    : null);
        // Session mint resolves the same production client that RC v2 targets. Creating the demo
        // user in Sandbox would make init succeed while every production session bootstrap is 404.
        QEnvironment effectiveEnvironment = DEFAULT_PROJECT_KEY.equals(projectKey)
                ? QEnvironment.Production
                : QEnvironment.Sandbox;

        QonversionConfig.Builder qonversionConfigBuilder = new QonversionConfig.Builder(
                this,
                projectKey,
                QLaunchMode.SubscriptionManagement
        ).setEnvironment(effectiveEnvironment);

        // Remote Config v2 has no default base URL — the pipeline stays dormant until a config is
        // supplied, and it is addressed independently of setProxyURL below (which only moves the
        // legacy REST API). minFetchIntervalSeconds is 0 so the demo is never throttled.
        qonversionConfigBuilder.setRemoteConfigV2Config(new QRemoteConfigV2Config(
                UtilsKt.RC_V2_PLAYGROUND_BASE_URL,
                UtilsKt.RC_V2_PLAYGROUND_ENVIRONMENT_UID,
                0,
                DEFAULT_PROJECT_KEY.equals(projectKey)
                        ? new LocalRemoteConfigIdentifyAssertionProvider()
                        : null
        ));

        NoCodesConfig.Builder noCodesConfigBuilder = new NoCodesConfig.Builder(
                this,
                projectKey
        ).setCustomFallbackFileName("fallbacks/nocodes_fallbacks.json");

        if (effectiveApiUrl != null) {
            qonversionConfigBuilder.setProxyURL(effectiveApiUrl);
            noCodesConfigBuilder.setProxyURL(effectiveApiUrl);
        }

        Qonversion.initialize(qonversionConfigBuilder.build());
        NoCodes.initialize(noCodesConfigBuilder.build());
    }
}
