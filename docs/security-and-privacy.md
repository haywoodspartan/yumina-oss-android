# Security, trust, and local data

[Documentation index](../README.md#documentation)

## Trust model

The user chooses the Yumina service origin. The app displays and executes the web UI delivered by that service, so the selected service must be trusted. Origin checks restrict native navigation and authenticated exports; they do not make an untrusted server's scripts or content trustworthy.

Three separate identities are involved:

| Identity | Purpose |
| --- | --- |
| Server account/session | Authenticates a player or administrator to the selected service |
| HTTPS server certificate | Authenticates the network endpoint, using normal trust or a narrowly scoped saved exception |
| APK signing certificate | Lets Android recognize the app publisher and compatible updates |

The client holds login cookies in WebView storage. Server records and model credentials remain subject to that service's policies. APK signing material exists on the build machine and is not packaged.

## Android permissions

The manifest declares only `android.permission.INTERNET`. The portable build checks the compiled APK and refuses a different permission set. It does not request microphone, camera, contacts, location, notifications, or broad storage permissions.

Uploads and exports use Android's document picker. The user's selection grants access to the selected document rather than unrestricted filesystem access. A voice or camera control shown by a server page does not establish native support for that feature; the client has no corresponding permission-grant implementation.

## Default HTTPS trust

No CA certificates are bundled in the APK. `network_security_config.xml` trusts Android system CAs and explicitly user-installed CAs through its `base-config`. Android supports declaring these trust sources in its [network security configuration](https://developer.android.com/privacy-and-security/security-config).

Ordinary HTTPS continues to use platform verification. Publicly trusted server connections do not need a saved exception. The user-installed CA source is an explicit app setting; it does not mean this APK installs a CA on the phone.

## Custom private server exceptions

For an unknown issuer, the app can save an exact leaf-certificate exception after user review. This supports self-signed certificates and certificates issued by a private CA. It does not trust that CA globally or authorize other certificates signed by it.

In WebView, the app checks that the error concerns the selected origin, that the primary error is `SSL_UNTRUSTED`, and that no other error flag is present. It decodes the presented certificate and checks its current validity. A previously accepted certificate must match its complete DER SHA-256 fingerprint for that HTTPS origin before the request can proceed.

A new or changed certificate is cancelled first. The native dialog shows the selected origin, subject, issuer, dates, and fingerprint; a changed certificate also displays the previous fingerprint. Only an explicit **Trust certificate** decision saves it and starts a fresh connection. A stale dialog cannot save trust after the view, origin, or navigation has changed.

Hostname mismatches, expired/not-yet-valid certificates, and other validation errors cannot be overridden. This feature intentionally permits a scoped unknown-issuer exception; it is not an unconditional SSL-error bypass.

The Android platform's [WebViewClient reference](https://developer.android.com/reference/android/webkit/WebViewClient) describes the SSL-error callback and its decisions. This application's explicit exception policy is implemented in `MainActivity` and `CertificateTrust`; it should be exercised on the intended WebView/device versions.

## Certificate storage and revocation

Public certificate DER bytes are stored in private `server_certificates` SharedPreferences. The key normalizes HTTPS scheme/host/default port. Another host or nondefault port uses a separate key. The stored bytes are bounded and decoded as a single X.509 certificate; corrupt or unavailable data is not trusted.

**App options → Server certificate** shows the saved entry for the selected server. **Forget certificate** removes it, clears WebView SSL preferences, replaces the view, and reconnects. Server changes and explicit reconnects also clear cached WebView SSL decisions so app-managed origin checks are reapplied.

Certificates are separate from web login data. Clearing local login/cache does not remove certificate exceptions. Clearing Android application data or uninstalling removes them. They are never exported into the source repository or future APKs.

## HTTPS exports

Normal URL exports remain restricted to the configured origin. Before opening each connection, the downloader checks the URL and applies a scoped TLS factory only for that origin's saved certificate.

The factory delegates to Android's normal trust manager first. If ordinary verification fails, its fallback requires an exact match to the saved leaf certificate and valid dates throughout the presented chain. A changed leaf, missing chain, or expired certificate cannot use that fallback. Client-certificate checks remain delegated and are not authorized by a server pin.

The normal `HttpsURLConnection` hostname verifier remains enabled. No global default socket factory or permissive hostname verifier is installed. Redirects are followed manually and origin-checked before cookies are forwarded.

## HTTP and mixed content

HTTP remains allowed for appropriate local/private configurations. An omitted address scheme currently defaults to HTTP; type `https://` explicitly for encrypted transport. HTTP has no certificate to accept, and the Server certificate menu explains that distinction.

WebView mixed content is set to `MIXED_CONTENT_NEVER_ALLOW`. Allowing a top-level HTTP origin and refusing insecure resources inside an HTTPS page are separate controls.

## Navigation and web-to-native controls

Origin comparison uses scheme, host, and effective port and rejects embedded user-info. Same-origin pages stay in the WebView. External main-frame HTTP(S) and mail links use Android's external applications; unsupported schemes are not handed to a general native intent dispatcher.

The exact `yumina-app://options` command opens native App options only from the configured current page, in the main frame, with a user gesture, and without a redirect. Extra actions, paths, or parameters fail that check.

No `addJavascriptInterface` bridge is exposed. Native code initiates limited `evaluateJavascript` calls for the options capability marker, `yuminaAndroidBack` hook, and bounded same-origin Blob exports. Navigation counters prevent old callbacks from controlling a newly loaded page.

## Cookies, uploads, and exports

First-party cookies are enabled; third-party WebView cookies are disabled. Cookies are flushed after page load and when the activity pauses. Native network exports use the configured origin's cookie and do not forward it to an unrelated redirect target.

File access and file-URL escalation are disabled. Explicit `content://` document-provider uploads remain available through the system picker. The native chooser processes up to 200 returned documents and cancels pending callbacks when the renderer is replaced.

Blob exports must originate from the current connected page and remain below 10 MiB. Export filenames have separators/line breaks sanitized. Destination writes run on a worker, and success is reported only after the stream closes. Interrupted writes can leave a partial file in the selected provider.

Only one export can run at a time. Server switching, login clearing, and certificate forgetting are blocked while a save is active. These guards avoid changing the transfer's account or trust context midway through the operation.

## Local data

The selected origin lives in private `connection` preferences. WebView holds cookies, cached pages, and web storage. Certificate exceptions live separately as described above. The manifest disables Android app backup.

The native application has no independent story/account database or local model runtime. Clearing local web data leaves server content intact. Uninstalling does not delete the selected service's records.

## Build secrets

`data/android-signing/release.p12` and `password.txt` remain private. The builder passes the password through `YUMINA_ANDROID_SIGNING_PASSWORD` to its signing helper. Data folders, outputs, common certificate/private-key file extensions, and local configuration are ignored by Git.

The APK signing identity is separate from any server certificate accepted on a phone. A new build checkout needs the intended privately backed-up APK key for compatible updates. CI creates a temporary verification key and does not publish it as a production identity.

## Verification limits

Automated tests use temporary real certificates to verify policy boundaries, persistence decoding, and the download trust manager. Compilation and APK inspection establish that the new classes/resources are packaged correctly. WebView dialogs, actual TLS handshakes on Android, device document providers, and live hosted/self-hosted sign-in remain device/deployment checks.
