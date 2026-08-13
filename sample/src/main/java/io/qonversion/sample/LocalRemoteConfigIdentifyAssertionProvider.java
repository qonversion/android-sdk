package io.qonversion.sample;

import com.qonversion.android.sdk.dto.remoteconfig.QRemoteConfigIdentifyAssertionCallback;
import com.qonversion.android.sdk.dto.remoteconfig.QRemoteConfigIdentifyAssertionProvider;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Local-playground bridge that models an app asking its authenticated backend for an assertion. */
final class LocalRemoteConfigIdentifyAssertionProvider implements QRemoteConfigIdentifyAssertionProvider {
    private static final String URL_STRING = "http://10.0.2.2:7089/remote-config-assertion";

    @Override
    public void requestAssertion(String externalUserId, QRemoteConfigIdentifyAssertionCallback callback) {
        new Thread(() -> callback.onResult(fetch(externalUserId)), "rc-v2-local-assertion").start();
    }

    private String fetch(String externalUserId) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(URL_STRING).openConnection();
            connection.setConnectTimeout(2_000);
            connection.setReadTimeout(2_000);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "text/plain; charset=utf-8");
            connection.setDoOutput(true);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(externalUserId.getBytes(StandardCharsets.UTF_8));
            }
            if (connection.getResponseCode() != 200) return null;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                return reader.readLine();
            }
        } catch (Exception ignored) {
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }
}
