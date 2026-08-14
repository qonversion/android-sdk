# Release notes draft — RC v2 train (Android SDK)

Status: DRAFT for the upcoming release from `release/rc-v2`. Merge the relevant sections into the
GitHub release description / changelog when the train ships.

## Breaking behavior change: TLS certificate verification is now enforced

Previous SDK versions disabled TLS verification on their own HTTP clients: the core API client
(`NetworkModule.provideOkHttpClient`) trusted every certificate and every hostname, and the NoCodes
module's `HttpsURLConnection` client did the same. This release removes both trust-all paths
(commits `2cb72d80`, `907c3dc7`). All SDK traffic is now verified against the device's trust store,
exactly like any other HTTPS traffic in your app.

**Who is affected**

- Apps inspecting SDK traffic through an intercepting proxy (Charles, Proxyman, mitmproxy, Fiddler)
  with a locally installed root certificate.
- Test or staging setups pointing the SDK at endpoints with self-signed or otherwise untrusted
  certificates (e.g. via a proxy URL).
- Corporate environments performing TLS interception without distributing the interception root to
  the Android user trust store.

**What you will see**

Requests from the SDK fail the TLS handshake — `SSLHandshakeException` /
`CertificateException`-style errors surfacing through the SDK as network errors. Production apps
talking directly to Qonversion over the public internet are not affected.

**What to do**

Do not re-introduce trust-all TLS. For debugging, declare your proxy's root CA as a trusted debug
certificate with Android's standard [network security configuration](https://developer.android.com/privacy-and-security/security-config):

```xml
<!-- res/xml/network_security_config.xml -->
<network-security-config>
    <debug-overrides>
        <trust-anchors>
            <certificates src="user" />
        </trust-anchors>
    </debug-overrides>
</network-security-config>
```

```xml
<!-- AndroidManifest.xml -->
<application android:networkSecurityConfig="@xml/network_security_config">
```

`<debug-overrides>` applies only to debuggable builds, so release builds keep full verification.

## Other changes worth calling out

- **Persistent Remote Config last-known-good cache.** Remote configs (v1 `remoteConfig` /
  `remoteConfigList`) are now persisted per project/environment/user and served when a fetch fails
  or the device is offline — previously such calls errored once the in-memory cache was gone after
  a restart. If your code treats a remote-config error as "no config", it will now more often
  receive the last successfully fetched values instead.
- **Stricter `remoteConfigList` parsing.** A malformed element in the response now fails the whole
  list call with `ResponseParsingFailed` instead of being silently skipped, so a partial list can
  no longer be mistaken for the full one.
- **New public API: `Qonversion.fallbackRemoteConfigValue(context, contextKey)`.** Synchronously
  reads a default from the `qonversion_remote_config_defaults.json` asset bundled with the app —
  available before SDK initialization and without any network.
