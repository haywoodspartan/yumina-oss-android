# Story Writer Android

**An Android client that works with the native [Yumina.io](https://yumina.io) service or a custom self-hosted version of Yumina, built with Java and Android WebView.** Use the official hosted service or connect to your own Yumina-based server to access its worlds, stories, chat, and account tools in an installable Android app.

This repository contains the Android application, its resources, connection tests, and build tools. Stories, accounts, memory, model configuration, and AI inference stay with the selected hosted service or self-hosted server; the phone supplies the interface and native device integration. Available features depend on the selected Yumina version and account permissions.

The existing installed app is labelled **Yumina** and keeps package ID **`ai.storywriter.mobile`**. The repository name does not change the installed application's identity. Source extraction preserves the current client behavior, existing public CA, and signing compatibility with the original workspace.

## At a glance

| Item | Current implementation |
| --- | --- |
| Platform | Android 8.0 and later; minimum API 26 |
| Current app version | 1.3.0, version code 8 |
| Compile / target SDK | 36 / 36 |
| Language | Java, compiled for Java 8 compatibility |
| UI | Server web interface inside Android WebView, plus native connection and recovery controls |
| Permission | `android.permission.INTERNET` |
| Server connection | User-selected HTTP or HTTPS origin |
| Supported destinations | Native Yumina.io hosted service or a custom self-hosted Yumina installation |
| HTTPS trust | System CAs, user-installed CAs, and one bundled public server CA |
| Files | Android document picker for uploads and Save file picker for exports |
| Portable build | Python 3.10+, Windows x64, downloaded and SHA-256-checked JDK/build tools |
| Alternative development | Android Studio / Android Gradle Plugin 8.13.2 |
| AI execution | On the connected server or its configured providers |

## What the app does

- Remembers the chosen server address between launches.
- Opens that server's web UI without an extra permanent address toolbar.
- Offers native **App options**: change server, reload, open in browser, clear local login/cache, and view help.
- Gives a usable recovery screen when the server is offline, returns an HTTP error, fails certificate checks, takes too long to load, or loses its WebView renderer.
- Integrates EPUB and image uploads with Android's document picker without broad storage access.
- Saves server downloads and supported browser-generated exports through Android's Save file dialog.
- Preserves login cookies in the WebView and flushes them when the app pauses.
- Handles Android back navigation, keyboard resizing, rotation, and system-bar insets.
- Opens external web and email links in Android's other applications.

Server features available on the phone depend on the connected server version and the account's permissions. The current associated server includes discovery, world creation, story chat, model selection, OOC and slash commands, World Library uploads and retrieval, and administrator tools. Those features are delivered by the server's web interface; their implementation is not included in this repository.

## Requirements for using the app

You need an Android 8.0+ device with a functioning WebView provider and an installed APK. For the native Yumina.io service, use an Internet connection and enter `https://yumina.io`. For a custom self-hosted version of Yumina, enter its reachable HTTP(S) origin; a private deployment may require the same Wi-Fi network or a VPN. Sign in with an account on the selected service when authentication is required.

| Connection choice | Address example | Hosting requirement |
| --- | --- | --- |
| Native Yumina.io | `https://yumina.io` | Internet access; no personal server required |
| Custom self-hosted Yumina | `https://stories.example.com` or `https://192.168.1.20` | Your installation must be running and reachable |

The standard WebView interface works independently of the optional custom-server App options and back-navigation hooks. Server-specific additions such as the World Library or custom administrator panels are available only where that deployment implements them.

The APK does not contain an AI model, an independent story database, a server launcher, or offline story generation. It does not currently include push notifications or an automatic APK updater.

## Install and connect

1. Obtain the signed APK from this app's maintainer or your server administrator. Custom deployments based on the original Story Writer server can also provide it at `https://YOUR-SERVER/app/android`; that download route is not required of the official Yumina.io service.
2. Download the signed APK and allow installation from that browser or file source when Android requests it.
3. Install and launch the app.
4. Enter `https://yumina.io` for the native hosted service, or your custom server's origin, for example `https://192.168.1.20` or `https://stories.example.com`.
5. Sign in and open your library or a chat.

Enter an address without an application path, query, fragment, or embedded username/password. `localhost`, `127.0.0.1`, `::1`, and `0.0.0.0` are rejected because they do not identify your remote server. An address without a scheme currently defaults to HTTP; type `https://` explicitly when using HTTPS.

Existing installations can be updated in place when the new APK has the same package ID and signing key and a suitable version code. Keep the release keystore and password in a private backup. A fresh clone cannot reproduce the original signing identity by itself.

## Build from this repository

The verified portable path is Windows x64 with Python 3.10 or later:

```powershell
git clone https://github.com/haywoodspartan/story-writer-android.git
cd story-writer-android
python build.py
```

The first build downloads the pinned tools listed in [tools/toolchain.lock.json](tools/toolchain.lock.json). Later builds reuse the cache after checking the file hashes. No global Java or Android SDK installation is required for this path.

A standalone clone writes:

```text
build/                         Intermediate compiled resources, Java classes, DEX and APKs
data/android-toolchain/        Downloaded build inputs and extracted toolchain
data/android-signing/          PRIVATE release.p12 and password.txt
downloads/yumina-android.apk   Verified signed APK
downloads/yumina-android.sha256
```

In the original Story Writer workspace, `python android/build.py` continues to use the parent project's `data/` and `downloads/` folders. Both layouts use the same source. Optional `STORY_WRITER_ANDROID_DATA_DIR` and `STORY_WRITER_ANDROID_OUTPUT_DIR` variables select explicit locations. See the [build guide](docs/build-and-development.md) before moving signing material or creating release APKs.

The build compiles resources and Java, runs the connection boundary tests, produces DEX, checks ZIP alignment, signs with APK signature schemes v2/v3, verifies signatures and manifest properties, and confirms the correct public CA is packaged before publishing the output files. A successful build does not establish that every device interaction works; use the [device test checklist](docs/releasing-and-testing.md#device-smoke-test).

## Documentation

The detailed documentation explains both user-facing behavior and the source implementation:

| Guide | Contents |
| --- | --- |
| [User guide](docs/user-guide.md) | Installation, connection, login, menus, files, back navigation, backgrounding, and device behavior |
| [Architecture](docs/architecture.md) | Native/server responsibilities, WebView setup, lifecycle, connection state, upload/export flows, and source map |
| [Build and development](docs/build-and-development.md) | Standalone setup, original workspace compatibility, paths, toolchain, signing, Android Studio, icons, and CI |
| [Security and privacy](docs/security-and-privacy.md) | Trust anchors, origin checks, cookie handling, file access, permissions, local data, and private build material |
| [Server integration](docs/server-integration.md) | Network setup, download hosting, native App options contract, back-navigation hook, and compatibility |
| [Releasing and testing](docs/releasing-and-testing.md) | Version updates, signing identity, automated checks, device matrix, distribution, CA rotation, and recovery |
| [Troubleshooting](docs/troubleshooting.md) | Connection, certificates, installation, file operations, build failures, and renderer recovery |
| [Reference and FAQ](docs/reference-and-faq.md) | Constants, configuration, repository contents, supported boundaries, and common questions |
| [Verification record](docs/verification.md) | What was checked for the standalone repository and what still needs device testing |

## Repository layout

```text
app/
  build.gradle
  src/main/
    AndroidManifest.xml
    java/ai/storywriter/mobile/
      MainActivity.java
      ServerAddress.java
    res/                       Icons, native styles, options artwork, public CA and trust config
docs/                          Detailed client documentation
tests/ServerAddressTest.java    Pure-Java address and origin boundary checks
tools/
  build helpers, pinned dependency lock, icon generator and source artwork
third-party/yumina/            Preserved upstream license and trademark notices
.github/workflows/android.yml  Windows portable-build verification
build.py                       Compile / test / sign / verify / publish pipeline
build.gradle                   Android Gradle Plugin declaration
settings.gradle                Gradle repository and module configuration
```

Generated files, downloaded tools, APK outputs, local IDE settings, and private signing material are excluded from Git. This repository does not include the server source, story collections, account databases, model weights, or the original workspace's `.env` files.

## Project origin and artwork

The Android shell was extracted from the Story Writer AI workspace, whose active server is based on [Yumina](https://github.com/lovetimo0421/yumina-oss). It is maintained here as a separate Android client repository.

The existing Yumina logo, generated launcher artwork, and installed display name are preserved from that workspace. Upstream code licensing and trademark notices are retained in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) and [third-party/yumina/](third-party/yumina/). The repository's Android source has not been given a new blanket license grant as part of this extraction.

Implementation and version details in these guides describe the source inspected on **October 6, 2026**. The guides distinguish implemented behavior from future possibilities and from checks that require a real device.
