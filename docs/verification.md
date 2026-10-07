# Standalone repository verification

[Documentation index](../README.md#documentation)

This record describes verification performed while preparing the Android-only repository on October 6, 2026. It does not claim a fresh device installation or a production server smoke test.

## Scope

The extraction includes the Android application source, resources, existing public CA, portable build pipeline, Gradle files, tests, local icon source, upstream notices, CI workflow, and detailed client documentation. Server code, databases, private signing material, caches, and generated APK outputs remain outside tracked source.

The builder retains the original workspace's data/output defaults and adds standalone defaults plus explicit environment overrides. Native Java behavior, package ID, app version, and bundled CA are unchanged.

## Verification results

| Check | Result |
| --- | --- |
| Source-only standalone checkout | Passed; exported only staged repository files into a separate directory |
| Standalone default paths | Passed; generated data/signing and downloads resolve inside the standalone checkout |
| Original workspace paths | Passed; original parent cache, signing folder and download locations are retained |
| Explicit data/output overrides | Passed; both overrides select their documented locations |
| Full portable build | Passed on Windows x64 with Python 3.13 |
| Fresh GitHub runner and cold tool downloads | Passed; all five locked inputs downloaded and the full verification pipeline completed |
| Pure-Java connection/origin tests | All 54 checks passed |
| APK v2/v3 signature verification | Passed using a newly generated disposable test key |
| Public CA validation and packaged-byte comparison | Passed |
| Signed/unsigned APK alignment | Passed |
| Compiled manifest identity, SDK and permission checks | Passed |
| Standalone icon regeneration | Passed using the locally preserved source artwork and Pillow |
| Local documentation links and heading anchors | Passed |
| Repository whitespace check | Passed |
| Tracked generated/private-file scan | Passed; no build/cache/download folders or private keystores tracked |
| Tracked credential/private-key pattern scan | Passed; no matching token/private-key material found |

The snapshot reused public build inputs from the existing verified tool cache via hard links. It had no pre-existing signing files and generated a disposable key under its own ignored data directory. The original installation's release key and public APK were not replaced by the test build.

The optional icon test used the existing developer Python environment with Pillow. Pillow remains unnecessary for a normal APK build using checked-in icons.

The JDK reported Java 8 compatibility/deprecated-API compiler warnings; they did not prevent compilation or verification. No blanket warning-free build claim is made.

The first [GitHub Windows verification run](https://github.com/haywoodspartan/story-writer-android/actions/runs/37568134512) completed successfully from the published source. It downloaded all five locked inputs on the runner, passed all 54 origin tests, and verified the signed APK. The workflow uses current checkout/Python-setup action releases for subsequent runs.

## Checks requiring a device or separate environment

The following remain deployment checks: actual APK installation/update on Android, live HTTPS connection to the server, sign-in, story/OOC generation, native menu behavior, document-provider uploads/exports, gesture/keyboard/rotation behavior, background/resume, renderer recovery, and the server's per-account permissions.

The portable pipeline is the build path exercised for this extraction. Android Studio/Gradle is documented as an alternative; it is not represented as verified here unless separately exercised.
