# Security, trust, and local data

[Documentation index](../README.md#documentation)

## Trust model

The user chooses the server origin. The app displays and executes the web UI delivered by that server, so the server must be trusted by the user. Origin checks restrict native navigation and authenticated exports; they do not make an untrusted server's own scripts or content trustworthy.

There are three distinct identities:

| Identity | Purpose |
| --- | --- |
| Server account/session | Authenticates a player or administrator to the remote application |
| HTTPS certificate chain | Verifies the server connection and its requested hostname |
| APK signing certificate | Lets Android recognize the app publisher and compatible updates |

The client holds login cookies in WebView storage. Server records and model credentials remain subject to the server's configuration and policies. Private APK signing material exists only on build machines and is not packaged.

## Requested Android permission

The application declares only `android.permission.INTERNET`. The portable build checks the compiled APK and refuses a different permission set. There are no manifest requests for microphone, camera, contacts, location, notifications, or broad storage access.

Document uploads and exports use Android's user-mediated document picker. The user's explicit document selection gives the app access to that selected resource. Permission to select a document is not equivalent to scanning the device's entire filesystem.

The main server UI may expose functionality that is not usable with this native permission set. In particular, a page showing a microphone/voice control does not establish native microphone support: the client has no audio permission or WebView permission-grant implementation for it.

## HTTPS validation

`network_security_config.xml` trusts Android system CAs, explicitly user-installed CAs, and the public root bundled as `res/raw/story_writer_ca.pem`. Android's declarative trust configuration supports these app-specific trust anchors; see the [official documentation](https://developer.android.com/privacy-and-security/security-config).

The bundled root is an **additional CA trust anchor**, not an exclusive certificate pin. Connections can also succeed through the configured system/user trust sources. The current configuration applies these sources through `base-config`, rather than limiting the bundled CA to one domain.

The WebView SSL error handler cancels invalid connections. It never calls `proceed()` to bypass a certificate error. A matching root does not excuse an expired certificate, a wrong hostname, or a wrong phone clock.

The current bundled public root's DER SHA-256 fingerprint is:

```text
AB07B8686CA188793597BFBA0E440DB83C335E272B4DB3F8841BE5750604089E
```

Its certificate validity ends June 20, 2036 at 07:31:37 UTC. The public PEM is intentionally tracked. No CA private key is included.

At build time, `VerifyCa` checks the root's validity period, CA basic constraints, matching subject/issuer, certificate-signing usage when present, and self-signature. The Python builder rejects private-key text and multiple certificate blocks, then verifies that the exact validated public PEM is retained inside the final APK.

These checks validate the packaged trust anchor. They do not connect to the live server or prove that its currently served certificate is valid.

## HTTP and mixed content

The application allows cleartext HTTP for trusted local-server configurations. Address normalization accepts HTTP, and an omitted scheme defaults to HTTP. Always include `https://` when encrypted transport is intended.

HTTP provides no TLS encryption or server authentication. Limit such deployments to an appropriate trusted network. The app does not silently upgrade an HTTP address to HTTPS.

For a loaded HTTPS page, WebView mixed content is disabled. The cleartext allowance and mixed-content rule are different settings: one allows an HTTP server origin; the other prevents an HTTPS page from mixing insecure resources through that WebView setting.

## Origin boundaries

`ServerAddress.sameOrigin` compares scheme, host, and effective port. Different schemes or ports represent different origins, even if the host text is the same. User-info is rejected so addresses such as `https://user@server` cannot be treated as an authorized native target.

Same-origin pages stay inside WebView. External main-frame HTTP(S)/email links open Android's external applications. Arbitrary unsupported schemes do not get forwarded through a generic native intent.

Blob export URLs must embed the configured server's origin. Foreign-origin Blobs, `blob:null`, and non-Blob URLs cannot pass the Blob check.

## Limited web-to-native integration

The client exposes no `addJavascriptInterface`. The exact `yumina-app://options` navigation command can open App options only from the connected origin's main page with a user gesture and without a redirect. Extra parameters, alternate commands, and subframe/scripted navigation fail the checks.

Native code initiates a few `evaluateJavascript` probes for the options marker, back-navigation hook, and bounded Blob exports. These calls are tied to the current navigation state. They do not offer web content an unrestricted native command dispatcher.

## Cookies and authenticated downloads

First-party WebView cookies are accepted; third-party cookies are disabled for this WebView. Cookies are flushed after page loading and when the activity pauses. Their contents are not committed to the repository or exported as a feature.

For normal URL exports, the app obtains the configured server's cookie header and checks every manually followed redirect before sending it. An export that leaves the origin fails instead of forwarding that cookie to the other server.

Browser-created exports are read only from the current connected same-origin page and are capped at 10 MiB. Their temporary page data is removed after retrieval on the normal completion path. Navigation/renderer changes invalidate the associated callback.

## File and lifecycle boundaries

File-URL access and file-URL cross-origin escalation are disabled. Explicit document-provider content access remains enabled for chosen uploads. The client accepts only returned `content://` chooser items and has no broad storage permission.

Export destination names are sanitized for separators and line breaks. Network writes occur on a worker executor. Successful saving is reported only after the destination stream closes; a failed write can leave a partial provider document.

Changing servers replaces the WebView and cancels a pending upload callback. Native code prevents changing the server or clearing login during an active export. Activity teardown cancels UI callbacks and destroys the renderer.

## Local storage and clearing data

The selected server origin lives in private SharedPreferences. Cookies, cached pages, and server web storage live in the app's WebView data area. The manifest disables Android app backup. The native client does not maintain a separate copy of server books, accounts, or the authoritative story database.

**Clear local login and cache** removes WebView cookies across servers, deletes web storage, clears cached pages, and replaces the WebView. It does not delete server content or reset the selected native server address. Uninstalling/clearing application data through Android also removes local connection configuration.

## Build-machine secrets

`data/android-signing/release.p12` and `password.txt` must remain private. The builder passes the password to the signing helper through a process environment variable. The source ignores data folders and common private key/keystore filenames.

The public root CA is the deliberate exception to the PEM ignore rule. Copy only a public root certificate from the server. Do not copy Caddy `root.key`, `intermediate.key`, a TLS private key, release keystores, server `.env` files, or server databases into app resources or public download folders.

CI uses a temporary key of its own. That key is a verification artifact rather than an update identity for users. Production release signing must use the private backed-up original key.

## Scope of verification

Source checks and packaging tests cover implemented boundaries. They are not a full application security audit. Device WebView behavior, server trust, actual TLS endpoints, account permissions, destination providers, and runtime networking need verification in the deployment where the app is used.
