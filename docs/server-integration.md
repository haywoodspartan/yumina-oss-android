# Server integration and compatibility

[Documentation index](../README.md#documentation)

## Separate deployment

The Android client works with the native [Yumina.io](https://yumina.io) hosted service or a custom self-hosted version of Yumina at a user-selected origin. Enter `https://yumina.io` for the official service, or the HTTP(S) origin of your own installation. It does not start that application or include its database. Custom installations can expose their application through a reverse proxy on private infrastructure.

A plain browser-compatible UI can load without implementing the optional native integrations. The integrated App options links, Android-specific menu behavior, and conversation reconnect behavior depend on the server frontend.

## Network access

Native Yumina.io requires Internet access and does not require the user to run a PC server. A custom self-hosted installation must be reachable through LAN, routed private network, VPN, or an appropriately deployed HTTPS address. A loopback-only backend cannot be accessed directly from another device. For example, a reverse proxy can accept connections on the host and forward them to a loopback application port.

Firewall rules, DNS, VPN routing, router settings, server process supervision, and model-provider connectivity belong to the server deployment. The APK cannot fix them. It has no server discovery or port-forwarding feature.

The configured value is an origin, such as `https://192.168.1.20`, rather than a deep link. The server can navigate to its own application routes after loading. Paths and embedded credentials are rejected by the native address dialog.

## TLS and custom certificates

No CA certificates are bundled in this APK. Standard HTTPS uses Android's system and user trust sources. For a self-signed certificate or a certificate issued by a private CA, the client can save a user-approved exception for the exact presented leaf certificate and configured HTTPS origin.

The app cancels an unknown-issuer connection first and displays its fingerprint, subject, issuer and validity dates. After the user chooses **Trust certificate**, a new connection can proceed only when the certificate matches the saved value and has no date or hostname error. That choice also applies to same-origin HTTPS file exports. It does not authorize other certificates signed by the same CA.

The administrator should supply the server certificate's SHA-256 fingerprint through a separate trusted channel. Use a certificate whose subject alternative names cover the hostname or IP that clients actually enter. A renewed or replaced private certificate requires a new explicit decision in the app. **App options → Server certificate → Forget certificate** removes the saved exception and clears cached WebView SSL decisions.

The app's trust choice does not install a CA on Android or change an external browser. See [Custom server certificates](custom-server-certificates.md) for the user flow and deployment guidance.

## Native App options contract

To integrate native options into the web UI, render a normal user-clickable main-page link with the exact destination:

```html
<a href="yumina-app://options">App options</a>
```

Only add this link in the Android client context. A regular browser does not have the native handler implemented by this activity.

On a page that reliably exposes integrated options, set the document-root marker:

```html
<html data-android-app-options="1">
```

`onPageFinished` checks that exact marker. If it is absent, the app shows a small fallback native overflow button. The marker is a UI capability flag, not a security permission.

To invoke the options menu, the navigation request must be a main-frame request with a user gesture from the configured origin's current page and must not be a redirect. The client intentionally rejects a query string, trailing slash, alternate action, or script-only command. The link does not carry a new server address or destructive instruction.

## Client detection

The user agent includes:

```text
YuminaAndroid/1.4.0
```

The associated server uses these identifiers to offer native options and suppress redundant APK download links inside the app. Keep native version text consistent when releasing a new APK. Custom frontends should expose the `yuminaAndroidBack` hook if they want to intercept Android back; otherwise the native client uses WebView history or exits.

User-agent detection is suitable for UI adaptation. It is not authentication: arbitrary clients can supply the same string.

## Back-navigation contract

A compatible page may provide a function named `window.yuminaAndroidBack`. It should synchronously return boolean `true` when it handled a back action, for example by closing a menu. Return false when native history/exit behavior should proceed.

```javascript
window.yuminaAndroidBack = function () {
  if (closeCurrentOverlay()) return true;
  return false;
};
```

`closeCurrentOverlay` is illustrative frontend logic, not an API provided by this repository. The hook should report whether it actually handled the action. A Promise or the string `"true"` does not satisfy the native boolean-result check.

The native activity uses WebView history or closes itself if the hook is unavailable or returns a non-true result. Native callbacks are checked against the current navigation generation.

## Upload controls

Use ordinary HTML file inputs in the server web UI. The client handles WebView's file chooser callback, including accept types and multiple-selection mode.

```html
<input type="file" accept=".epub,application/epub+zip">
<input type="file" accept="image/*" multiple>
```

The chosen documents are uploaded through the server UI's existing code. The server still enforces its own authorization, format validation, and size limits. Native chooser support does not add a server upload route.

## Export compatibility

For direct exports, return a same-origin downloadable URL. Appropriate `Content-Type` and `Content-Disposition` headers improve naming and picker behavior. Authentication can use the WebView cookie for that origin; redirects must stay on that origin.

For page-generated exports, same-origin Blob URLs are supported up to 10 MiB. An anchor with the matching Blob href and `download` attribute lets the client preserve the suggested filename. A cross-origin signed-object-store redirect will not be followed by the authenticated native save flow; offer a supported same-origin route or an external-browser workflow if your deployment uses that approach.

## Host the APK and checksum

A custom server may expose `/app/android` as an installation page and read `/downloads/*` files from a configured downloads directory. The client repository does not contain that route handler.

The portable build produces:

```text
yumina-android.apk
yumina-android.sha256
```

Place those final files in the server's public downloads location, or configure the build's `YUMINA_ANDROID_OUTPUT_DIR` to publish there. Configure your server's download route to serve that directory. Never point public download hosting at the private `data/android-signing` directory.

If you offer a public CA download for browser users, distribute only the public certificate and a verified fingerprint. This is independent of the Android client's per-server certificate choices.

## Compatibility limits

The app does not negotiate a formal protocol version with the server. It relies on web compatibility and the optional marker/hooks above. A server update may change available UI without changing the APK.

The native client has no microphone or camera permission, no service-worker-based offline promise, no push-notification service, no generic external intent bridge, and no automatic APK updater. Design server mobile flows around those implemented capabilities and test them on actual devices.
