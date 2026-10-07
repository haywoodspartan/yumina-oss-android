# Releasing and testing

[Documentation index](../README.md#documentation)

## Preserve installed-app identity

The current package ID is `io.github.haywoodspartan.yumina.android`; current version name/code are `1.4.0` / `9`. The installed label is Yumina OSS Android. This identity is a separate installation from apps with another package ID.

For an in-place update, keep the package ID and intended release signing identity. Use an increased version code for a new release. The private keystore and password must be restored before building from a fresh clone if the output is intended to update existing installations.

Generating a new key is suitable for a separate test installation identity, but it will not replace the original release. Android Studio's debug identity also differs from the portable release key.

## Version update locations

The native version is currently repeated in several files; it is not centralized.

| Location | Values to review |
| --- | --- |
| `app/build.gradle` | `versionCode`, `versionName` |
| `build.py` | AAPT2 `--version-code` and `--version-name` arguments |
| `MainActivity.java` | User-agent version identifier and About dialog version |
| `README.md` and client docs | Current version descriptions and examples |

Keep Gradle and portable version values synchronized. A user-agent change also affects the server's native-client detection contract. Do not increase the code merely to change documentation; increase it for a new distributable native release.

## Automated build checks

`python build.py` verifies the following before publishing the distributable output:

- All downloaded/cached build-input SHA-256 values match the lock file.
- Per-server certificate trust rejects wrong origins, changed certificates, expired/not-yet-valid certificates, and invalid stored data.
- Android resource compilation/linking and Java compilation succeed.
- The pure-Java server-address/origin checks pass.
- D8 generates usable DEX output.
- Uncompressed ZIP entry data is four-byte aligned in unsigned and signed APKs.
- APK signatures verify with schemes v2 and v3.
- Compiled package, launcher activity, minimum/target SDK, and Internet-only permission set match the intended values.

`SignApk` prints the APK signing certificate fingerprint. Record it privately alongside release provenance when appropriate; do not confuse it with an HTTPS server-certificate fingerprint.

The connection test harness covers valid LAN and IPv6 cases, default ports, scheme/host normalization, credentials/path/query rejection, origin confusion, Blob origin checks, and native options command restrictions. It does not drive a WebView or emulate a document provider.

## Device smoke test

Complete this sequence before distributing a changed native APK. Record the device, Android version, WebView version/provider, server version, and APK version/signing identity.

| Test | Expected result |
| --- | --- |
| Fresh install and launch | Server dialog appears; no broad permission prompts |
| Valid HTTPS LAN origin | Page loads with correct trust/hostname validation |
| Invalid address | Native validation explains the problem before connection |
| Multi-user sign-in | Account opens with its existing server permissions |
| App options in supported UI | Native menu opens from the integrated link |
| Older UI without marker | Fallback overflow button remains reachable |
| Story and OOC turn | Server UI sends and displays the requested turn |
| Change model through server UI | Server applies the supported selection |
| EPUB/image upload | System picker supplies the selected file to the page |
| Normal authenticated export | Save picker opens; file completes and opens correctly |
| Small Blob export | Correct bytes and suggested filename are retained |
| Oversized Blob export | App reports the bound and does not claim a saved file |
| Export destination failure | Error is shown; partial-file possibility is understood |
| Back while overlay is open | Web hook closes the overlay when implemented |
| Back with history / no history | Previous page or activity exit behaves correctly |
| Rotation and keyboard | Content/input stays usable and clear of system bars |
| Background during generation | Return to chat reconnects according to server behavior |
| Network loss | Recovery remains accessible; retry can reconnect |
| Unknown private issuer | Fingerprint dialog appears; Cancel blocks, Trust certificate reconnects to that origin |
| Saved custom certificate | Subsequent connections and same-origin exports accept only that valid certificate |
| Changed private certificate | New decision required; old exception does not authorize the replacement |
| Expired/future or hostname-mismatched certificate | Connection blocked even if a saved exception exists |
| Forget certificate | Exception removed; a new connection requires normal trust or a fresh decision |
| Renderer loss | Recovery screen appears with retry/options |
| Change server | New origin/history loads; old callbacks do not alter the new page |
| Clear local login/cache | App signs out and clears web data without deleting server stories |
| External link | Opens appropriate browser/email application |
| Upgrade existing release | Same-package, same-signing-key update retains local state |

At minimum, exercise Android 8 and a current supported Android release when distributing across devices. Include gesture navigation, keyboard behavior, and different document providers. A build/signature pass is not a substitute for these checks.

## Distribution procedure

1. Review native changes, version values, server compatibility, and docs.
2. Restore/check the intended private signing files; keep a private backup.
3. Build through the portable pipeline or a separately verified release path.
4. Run device checks relevant to the changes and intended audience.
5. Distribute only the final `yumina-android.apk` and its matching checksum.
6. Copy them to the server's public downloads directory or an explicitly prepared release location.
7. Install the update over an existing release as a final compatibility check.

The repository's CI workflow performs an isolated verification build. It does not create a GitHub Release or publish production downloads. Do not distribute its disposable-key APK as an update to existing installations.

## Custom server certificate rotation

This APK ships without a server CA bundle. Standard trust follows Android's configured system/user trust sources. Users of private infrastructure can explicitly accept the current server's leaf certificate in the native fingerprint dialog.

When that certificate is renewed or replaced, a saved exception no longer matches it. Supply the new SHA-256 fingerprint to users through a separate trusted channel so they can review the changed-certificate dialog. Ensure the new certificate covers the address clients enter and has a valid date range. A trust decision for one origin does not apply to another host or port.

Certificate acceptance is device-local and separate from APK signing. An administrator does not need to distribute an APK containing a private CA. Never distribute a TLS private key or an APK signing keystore.

## Version 1.4.0

This version uses the Yumina OSS Android package identity, Yumina-only environment variables and integration hooks, checkout-local build state, and explicit per-server certificate acceptance. It removes bundled server CA material. Update custom web frontends to `window.yuminaAndroidBack()` if they use the optional back hook; normal native history/exit behavior works without it.

## Recovery and rollback

Keep previous signed release APKs outside Git if you need a deployment archive. Android may refuse installing an older version code over a newer build; ordinary sideloading is not an automatic rollback mechanism. Server UI rollback is a separate operation because the page is served remotely.

Loss of the original signing key prevents producing ordinary compatible updates for installations signed by it. The source repository does not contain a recovery copy of that key. Restore the private backup rather than generating a replacement and expecting compatibility.
