# Third-party notices and project origin

This repository contains Yumina OSS Android, an Android client for the official Yumina.io service and custom self-hosted versions of [Yumina](https://github.com/lovetimo0421/yumina-oss). Server source and server dependencies are not included in this Android repository.

## Preserved Yumina artwork and identity

`tools/logo.png` preserves the Yumina artwork. The launcher images under `app/src/main/res/mipmap-*` were generated from that artwork. The manifest's current installed display name is `Yumina OSS Android`.

The upstream project identifies its code license as AGPL-3.0-only and publishes a separate trademark policy for the Yumina name and logo. Copies of those notices are retained verbatim:

- [Upstream LICENSE](third-party/yumina/LICENSE)
- [Upstream TRADEMARK.md](third-party/yumina/TRADEMARK.md)

These copies preserve upstream notices. They do not grant additional rights to the name/logo or apply a new blanket license to all Android shell source. The upstream artwork notices remain applicable to the included assets.

## Build-time tools

The portable build downloads the independently distributed tools named in `tools/toolchain.lock.json`: Eclipse Temurin JDK 21, Android AAPT2, R8/D8, Android apksig, and Robolectric Android framework resources. Their downloads are used as build inputs and remain in an ignored cache. This repository does not redistribute those binary toolchains.

The portable APK includes compiled application resources and DEX. It does not package the downloaded JDK, build-tool JARs, Android framework JAR, signing keystore, or signing password.

The optional icon generator uses Pillow, installed separately by the developer. Pillow is not needed to build the application from the checked-in icons and is not part of the APK.
