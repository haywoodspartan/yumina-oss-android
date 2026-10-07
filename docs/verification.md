# Verification record

[Documentation index](../README.md#documentation)

This record covers Yumina OSS Android **1.4.0**, package `io.github.haywoodspartan.yumina.android`, version code **9**.

## Source and behavior

The Android project owns its own build state and uses `YUMINA_ANDROID_*` environment variables. The application, test packages, signing alias/subject, browser user agent, and optional back-navigation hook use the Yumina identity.

The APK has no bundled CA resource. System/user trust remains available, and the native UI can save an exact certificate exception for an explicitly approved custom HTTPS origin. Native same-origin exports use the same exception while retaining hostname verification.

## Automated validation

| Check | Result |
| --- | --- |
| Full portable Windows build | Passed with Python 3.13 and the locked JDK/tools |
| Server address/origin suite | All 54 checks passed |
| Certificate trust suite | All 40 checks passed using temporary real X.509 fixtures |
| APK v2/v3 signature verification | Passed |
| Signed and unsigned ZIP alignment | Passed |
| Manifest package, version, activity, SDK and permission checks | Passed |
| Current tracked source and APK identity scan | Passed; no previous project branding remains |
| Tracked files and APK certificate/key resource scan | Passed; no bundled CA or private signing material |
| Documentation links, anchors, and Markdown fences | Passed |

The certificate suite covers normalized HTTPS origins, port/host/scheme isolation, changed fingerprints, expired and not-yet-valid certificates, DER decoding/size/trailing-data rejection, scoped export factories, invalid chains, trust removal, client-auth isolation, and continued normal platform trust. Temporary fixture keys/certificates are deleted by the test and are not application resources.

The JDK reports Java 8/deprecated-API warnings; these do not prevent compilation or verification. Android Studio/Gradle is documented as an alternative build path but has not been exercised for this update.

## Device checks still required

Actual Android installation, WebView trust dialogs, Cancel/Trust/Forget flows against a private HTTPS server, certificate rotation, trusted same-origin exports, public hosted sign-in, back gestures, keyboard/rotation, and background recovery require a device or emulator. The automated policy tests do not claim those interactions were run.

The [GitHub verification workflow](https://github.com/haywoodspartan/yumina-oss-android/actions/workflows/android.yml) runs the same build and both test suites on a fresh Windows runner for published changes. Its signing key is disposable and is not a release update key.
