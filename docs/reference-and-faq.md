# Reference and FAQ

[Documentation index](../README.md#documentation)

## Application identity

| Property | Value |
| --- | --- |
| Repository | `haywoodspartan/yumina-oss-android` |
| Supported service choices | Native Yumina.io or a custom self-hosted version of Yumina |
| Current installed label | `Yumina OSS Android` |
| Application ID / namespace | `io.github.haywoodspartan.yumina.android` |
| Launcher activity | `io.github.haywoodspartan.yumina.android.MainActivity` |
| Version name / code | `1.4.0` / `9` |
| Minimum Android | 8.0, API 26 |
| Compile / target SDK | 36 / 36 |
| Java source target | Java 8 |
| Gradle plugin | 8.13.2 |
| Native theme | Platform Material, no action bar |
| Declared permission | `android.permission.INTERNET` |
| Android backup | Disabled |

## Runtime constants and contracts

| Item | Current value / behavior |
| --- | --- |
| Saved connection | SharedPreferences `connection`, key `server` |
| Default scheme when omitted | `http` |
| Native options link | Exact `yumina-app://options` |
| Integrated-options marker | Document root `data-android-app-options="1"` |
| Back hook | `window.yuminaAndroidBack()` returning boolean `true` when handled |
| Client user-agent suffix | `YuminaAndroid/1.4.0` |
| Main-page load timer | 30 seconds |
| Native network-export connect/read timeouts | 15 / 30 seconds |
| Network-export redirect loop | Up to six request attempts; same-origin only |
| Blob export bound | 10 × 1024 × 1024 bytes |
| Blob poll schedule | 100 ms delay with a bounded attempt count |
| Maximum chooser items processed | 200 |
| Native transfer executor | One worker thread |
| Saved certificate exceptions | Private `server_certificates` preferences, keyed by canonical HTTPS origin |
| Certificate identity | Exact leaf certificate SHA-256; no bundled CA certificates |
| Allowed external link schemes | HTTP, HTTPS, mailto |
| Accepted saved/upload file source | Android document-provider content URI |

## Build configuration

| Setting | Meaning |
| --- | --- |
| `YUMINA_ANDROID_DATA_DIR` | Optional cache/signing base directory |
| `YUMINA_ANDROID_OUTPUT_DIR` | Optional final APK/checksum directory |
| `YUMINA_ANDROID_SIGNING_PASSWORD` | Internal environment handoff from Python to Java signing helper |
| `tools/toolchain.lock.json` | Exact binary download URLs and SHA-256 hashes |
| Keystore alias | `yumina-android` |
| Keystore format | PKCS#12 |
| Newly generated key | RSA, 3072 bits |
| Enabled APK signature schemes | v2 and v3 |
| Portable output | `yumina-android.apk`, `yumina-android.sha256` |
| ZIP storage | Uncompressed resource/DEX entries, four-byte-aligned data |

No runtime `.env` file is consumed by the Android app. The server has its own configuration. The build reads process environment variables for the optional overrides above.

## Included and excluded files

The repository includes Java/XML application source, checked-in native artwork, Gradle descriptors, the portable builder, Java build helpers, address tests, the binary lock file, CI configuration, documentation, and preserved third-party notices.

It excludes generated builds, JDK/Android tool binaries, keystores/password files, downloadable APKs, IDE-local files, server source, server databases, imported stories/media, model weights, and account/provider secrets. APK distribution is a separate build output/release operation.

## What does “Android client” mean here?

The app is an Android-specific installable APK with native lifecycle, file, navigation, and recovery handling. Its main product UI comes from a separate web server inside WebView. It is not a separate offline implementation of the storytelling platform.

## Can I build it without the main app checkout?

Yes. The portable builder now detects a standalone checkout, stores generated data beneath it by default, and uses the icon source preserved in this repository. You still need a compatible server to use the installed client.

## Can I run a model on the phone?

No model runtime or weights are included. The connected server selects and calls its local or hosted model providers. Changing that arrangement would require new functionality rather than a configuration flag in this client.

## Does it work offline?

The native error/recovery screen works when the server is unavailable. The repository does not implement offline story generation, an offline story database, or a promised offline application mode. Cached WebView content is not a substitute for an available server.

## Does every server feature work on Android?

Only to the extent supported by the server's mobile UI, Android WebView, and this client's integrations/permissions. The native manifest has no microphone or camera permission. The presence of a voice control on the web page is not proof of usable native voice capture.

## Can I connect to a different server?

Yes. It works with the native Yumina.io service at `https://yumina.io` or a custom self-hosted version of Yumina. The app accepts valid user-selected HTTP(S) origins and retains same-origin boundaries. The selected server must offer a compatible web UI and valid transport trust. Private issuers can be accepted through an explicit certificate-fingerprint decision scoped to that server.

## Do I need to run a PC server to use native Yumina.io?

No. Choose `https://yumina.io`, connect to the Internet, and use your account on that hosted service. Keeping a personal server running applies to custom self-hosted deployments. Available features and sign-in flows come from the selected deployment.

## Does the APK contain CA certificates?

No. Standard TLS uses Android's system/user CA sources. A user can approve an exact server certificate after reviewing its fingerprint when its issuer is unknown. The saved exception belongs to that HTTPS origin, does not trust the issuer globally, and is not packaged into builds.

## What happens when a custom server certificate changes?

A saved exception matches the exact certificate. A replacement with an unknown issuer requires a new fingerprint review. A certificate accepted normally by Android uses normal platform trust. Dates and hostnames still have to be valid. Remove an exception in App options → Server certificate.

## Do downloads use the accepted certificate?

Yes. Same-origin HTTPS exports use the platform trust manager with a fallback for the exact accepted certificate. The normal hostname verifier remains active, and redirects to another origin are rejected before cookies are forwarded.

## Does clearing login remove stories?

No. It clears WebView cookies, cached pages, and web storage on the phone. Server content remains under the server's control. The selected native server address is retained by that menu action.

## How is the Android client identified?

The installed label is Yumina OSS Android, the package ID is `io.github.haywoodspartan.yumina.android`, and the browser user agent adds `YuminaAndroid/1.4.0`. Custom frontends can use `window.yuminaAndroidBack()` for native back navigation. The package ID is independent of the server account: users sign in to their chosen service after installing.

## Why are APKs and signing files absent from Git?

APKs are generated distribution outputs. The keystore/password are private release identity material. A reproducible source checkout should not contain a private publisher key. Existing users' updates require a separately restored private signing backup.

## Are there push notifications or automatic updates?

Neither is implemented in this native source. Install native APK updates manually from the administrator's distribution location. Server web UI updates can appear on reload because the interface is served remotely.

## Can I use the Android Studio project instead?

Yes, with a compatible Android SDK/JDK/Gradle setup. There is no checked-in wrapper or portable release signing configuration in Gradle. Debug builds have their own signing identity, and the pure-Java test harness is run separately from Gradle.

## What is tested automatically?

Compilation, connection-origin checks, certificate exception scope/expiry/identity, APK ZIP alignment, v2/v3 signature verification, compiled manifest identity/version/SDK values, and the exact permission set. Device interaction and live server behavior require the manual test matrix.

## Primary implementation references

- [Android WebView guide](https://developer.android.com/develop/ui/views/layout/webapps/webview)
- [Android network security configuration](https://developer.android.com/privacy-and-security/security-config)
- [Android Gradle Plugin 8.13 compatibility notes](https://developer.android.com/build/releases/agp-8-13-0-release-notes)
- [Associated upstream Yumina project](https://github.com/lovetimo0421/yumina-oss)

These references explain platform facilities and project origin. The source in this repository is the authority for this client's actual settings and behavior.
