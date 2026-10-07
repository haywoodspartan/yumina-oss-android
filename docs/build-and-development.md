# Build and development

[Documentation index](../README.md#documentation)

## Supported build paths

The portable Python builder is the verified build path for Windows x64. It needs Python 3.10+ and network access for the first download of pinned inputs. The script uses Python's standard library.

The Gradle project supports conventional Android Studio development with Android SDK 36. It is a separate build path and does not include a checked-in Gradle wrapper. The verification record documents which path was actually exercised.

## Standalone checkout

```powershell
git clone https://github.com/haywoodspartan/yumina-oss-android.git
cd yumina-oss-android
python build.py
```

The repository is private at creation, so cloning requires access to the owner's repository. No server checkout, PostgreSQL installation, Node runtime, model weights, global JDK, or global Android SDK is needed for the portable APK build.

The compiled application still needs a running compatible server when installed. Build independence and offline application functionality are different properties.

## Data and output layout

`build.py` locates the Android source from its own file, so it can be invoked from another working directory. By default, it keeps standalone generated state inside the checkout.

| Path | Contents | Git policy |
| --- | --- | --- |
| `build/` | Resources, generated R classes, Java classes, DEX, helpers, intermediate/signed APK | Ignored |
| `data/android-toolchain/` | SHA-256-verified downloads and extracted JDK/AAPT2 | Ignored |
| `data/android-signing/release.p12` | Private PKCS#12 release key | Ignored; back up privately |
| `data/android-signing/password.txt` | Password used by the build helper | Ignored; back up privately |
| `downloads/yumina-android.apk` | Verified distributable signed APK | Ignored |
| `downloads/yumina-android.sha256` | APK checksum | Ignored |

The builder always uses the Android checkout as its default root. It does not inspect a parent project's launchers or server folders. Use the explicit overrides below to share a public tool cache or select a private signing/output location.

## Explicit locations

Two optional environment variables override the defaults:

| Variable | Meaning |
| --- | --- |
| `YUMINA_ANDROID_DATA_DIR` | Base directory containing `android-toolchain/` and `android-signing/` |
| `YUMINA_ANDROID_OUTPUT_DIR` | Public output directory for the final APK and checksum |

Use absolute paths to make the intended location clear. Relative overrides resolve against the process working directory. The overrides are local process settings, not files automatically read from a `.env`.

```powershell
$env:YUMINA_ANDROID_DATA_DIR = 'D:\PrivateBuilds\yumina-android-data'
$env:YUMINA_ANDROID_OUTPUT_DIR = 'D:\PublicDownloads\yumina-android'
python build.py
```

The data override changes **both** the cache and signing-key directory. Pointing it at an empty location causes a new signing identity to be generated. To create updates for existing users, restore the intended private `android-signing/release.p12` and `password.txt` there before building.

Never configure the output folder to be the private signing folder. Output folders may be served by a website or shared with users.

## Pinned toolchain

`tools/toolchain.lock.json` records each binary download's URL and SHA-256. The current entries are:

| Input | Version / purpose |
| --- | --- |
| `jdk21.zip` | Temurin 21.0.12.1+1, Windows x64 compiler/runtime |
| `android.jar` | Robolectric Android 16 framework resources, build input only |
| `aapt2.jar` | AAPT2 9.4.1-15978811, Windows resource compiler/linker |
| `r8.jar` | R8 9.4.26, used through D8 for DEX generation |
| `apksig.jar` | Android apksig 9.4.1, signing and verification |

Downloads run concurrently, but compilation and publication occur in sequence. Every cached file is hashed again before use. A checksum mismatch stops the build. A corrupt cached file should be removed by exact filename and downloaded again; do not bypass the hash check.

The first run needs substantial download space for framework resources and the JDK. The cache is reusable and is not committed or packaged into the APK.

## What a build does

1. Reject a non-Windows host for the pinned portable pipeline.
2. Download/check all locked inputs and extract the JDK/AAPT2 when missing.
3. Clear known compiled classes, DEX and generated R classes so a namespace change cannot carry stale resource classes into the new APK.
4. Compile/link Android resources with the Yumina package ID, SDK levels, and version metadata.
5. Compile application Java and generated resource classes for Java 8 compatibility.
6. Compile/run the address and certificate-trust test suites. Certificate fixtures are generated temporarily by the test and removed afterwards.
7. Package classes and run D8 to create DEX.
8. Assemble an uncompressed, four-byte-aligned APK and verify alignment.
9. Load the checkout's private signing identity or generate a new one.
10. Sign and verify APK v2/v3 schemes.
11. Verify alignment again, manifest package/activity/version/SDK values, and the exact Internet-only permission set.
12. Publish the APK through a temporary file replacement, then write its SHA-256 companion.

The current checksum file is named `yumina-android.sha256` and contains the digest followed by `yumina-android.apk`. The signed intermediate output is `build/yumina-android.apk`.

## Signing identity

The build uses a PKCS#12 keystore with alias `yumina-android`. New identities use RSA 3072-bit keys. The signing password is generated into the ignored password file and passed to Java through the `YUMINA_ANDROID_SIGNING_PASSWORD` process environment.

If an existing keystore has no password file, the build stops and asks for the private backup. It does not discard the key and silently replace it.

Saved server certificates and the private APK signing key serve different purposes. The former authorize a specific private HTTPS endpoint after user review; the latter lets Android recognize updates from the same publisher. Server trust choices are made on the phone and are not build inputs.

## Android Studio and Gradle

Open this repository directory as an Android Studio project. The checked-in configuration declares AGP 8.13.2, compile/target SDK 36, minimum SDK 26, and Java 8 source/target compatibility. AGP 8.13's compatibility table specifies Gradle 8.13 and JDK 17; see the [official release notes](https://developer.android.com/build/releases/agp-8-13-0-release-notes) when selecting an IDE/Gradle runtime.

There is no `gradlew` in this repository. Use Android Studio's configured Gradle installation or install a compatible Gradle and run:

```powershell
gradle :app:assembleDebug
```

A debug build uses a different signing identity and cannot replace the existing portable release in place. The Gradle source does not define the portable release signing configuration. Configure release signing privately in Android Studio if you use that route. The portable `ServerAddressTest` is not declared as a Gradle JUnit test; run the harness separately or use the portable build to execute it.

## Run the pure-Java test harness separately

With a JDK on PATH, these commands run the address/origin checks without Android SDK access:

```powershell
New-Item -ItemType Directory -Path build/address-tests -Force | Out-Null
javac -encoding UTF-8 --release 8 -d build/address-tests app/src/main/java/io/github/haywoodspartan/yumina/android/ServerAddress.java app/src/main/java/io/github/haywoodspartan/yumina/android/CertificateTrust.java tests/ServerAddressTest.java tests/CertificateTrustTest.java
java -cp build/address-tests io.github.haywoodspartan.yumina.android.ServerAddressTest
java -cp build/address-tests io.github.haywoodspartan.yumina.android.CertificateTrustTest
```

The address harness covers allowed LAN addresses, scheme/host case, effective ports, rejected origin confusion, credentials/paths/queries, Blob boundaries, and native App options command conditions. The certificate harness uses temporary real X.509 fixtures to check origin isolation, exact fingerprints, changed certificates, expiry, future validity, persistence decoding, and download trust. Neither suite requires an emulator.

## Icons and optional Pillow

Launcher resources are checked in. Ordinary APK builds do not regenerate them and do not need Pillow. `tools/logo.png` is the preserved source artwork, now stored inside this repository so regeneration does not depend on a sibling server checkout.

To regenerate after an intentional source-artwork change:

```powershell
python -m pip install Pillow
python tools/make_icons.py
```

The generator writes adaptive background/foreground/monochrome layers and legacy PNGs for five densities, plus adaptive XML. Review the outputs before committing. See the [third-party notices](../THIRD_PARTY_NOTICES.md) for the preserved artwork's origin.

## Continuous integration

`.github/workflows/android.yml` runs the portable build on Windows for pushes to `main`, pull requests, and manual dispatch. It redirects data and public output to the runner's temporary directory. Its new signing key is disposable and is not the release key used by installed users.

The job proves compilation and automated packaging checks in a fresh environment. It does not publish a GitHub Release or upload a production APK. No release signing secret is stored in the workflow.

## Development discipline

Keep native connection and file integration changes in this repository. Treat server features and routes as a separate project. Update documentation when the native/server contract changes, keep version values consistent across build paths, and run a build plus relevant device checks before distributing a changed APK.
