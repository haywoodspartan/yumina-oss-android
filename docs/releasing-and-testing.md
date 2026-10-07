# Releasing and testing

[Documentation index](../README.md#documentation)

## Preserve installed-app identity

The current package ID is `ai.storywriter.mobile`; current version name/code are `1.3.0` / `8`. The application currently displays the name Yumina. Changing this repository's name does not change any of those APK properties.

For an in-place update, keep the package ID and intended release signing identity. Use an increased version code for a new release. The private keystore and password must be restored before building from a fresh clone if the output is intended to update existing installations.

Generating a new key is suitable for a separate test installation identity, but it will not replace the original release. Android Studio's debug identity also differs from the portable release key.

## Version update locations

The native version is currently repeated in several files; it is not centralized.

| Location | Values to review |
| --- | --- |
| `app/build.gradle` | `versionCode`, `versionName` |
| `build.py` | AAPT2 `--version-code` and `--version-name` arguments |
| `MainActivity.java` | Both appended user-agent version identifiers and About dialog version |
| `README.md` and client docs | Current version descriptions and examples |

Keep Gradle and portable version values synchronized. A user-agent change also affects the server's native-client detection contract. Do not increase the code merely to change documentation; increase it for a new distributable native release.

## Automated build checks

`python build.py` verifies the following before publishing the distributable output:

- All downloaded/cached build-input SHA-256 values match the lock file.
- The public CA resource has one certificate block and no private-key text.
- The certificate passes validity, CA properties, usage, and self-signature checks.
- Android resource compilation/linking and Java compilation succeed.
- The pure-Java server-address/origin checks pass.
- D8 generates usable DEX output.
- Uncompressed ZIP entry data is four-byte aligned in unsigned and signed APKs.
- APK signatures verify with schemes v2 and v3.
- The final APK contains the exact verified CA resource.
- Compiled package, launcher activity, minimum/target SDK, and Internet-only permission set match the intended values.

`SignApk` prints the APK signing certificate fingerprint. Record it privately alongside release provenance when appropriate; do not confuse it with the bundled server CA fingerprint.

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
| Invalid HTTPS certificate | Connection is cancelled, not bypassed |
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

## Replace or rotate the server CA

If the server's private CA changes, the old bundled root may no longer validate that server. Obtain the new **public root certificate** from the administrator and verify its identity out of band.

Replace `app/src/main/res/raw/story_writer_ca.pem` with that public root. Update any public certificate/fingerprint downloads served by the separate server, update this documentation's fingerprint/validity details, increase the native version code, and rebuild with the same APK signing key. Test a live connection before distribution.

For the original Caddy deployment, the public root is normally `%APPDATA%/Caddy/pki/authorities/local/root.crt`. Never copy `root.key` or `intermediate.key` into the app or downloads. A new public trust anchor does not require replacing the APK publisher key.

An alternative deployment can use a valid publicly trusted certificate without changing the bundled root. The client also trusts configured system/user CA sources; the bundled root is not an exclusive pin.

## Historical native changes

| Version | Recorded native changes |
| --- | --- |
| 1.2.0 | Display name changed from Story Writer to Yumina; package ID retained |
| 1.2.1 | Launcher icon changed to the associated Yumina artwork |
| 1.2.2 | Clear history on server changes; cancel upload callbacks on renderer replacement; preserve Blob filenames; report saves after stream closure |
| 1.3.0 / code 8 | Move connection controls into App options, remove the permanent native address toolbar, retain fallback controls for older server UIs |

Repository extraction adds standalone build/data paths, local icon source, documentation, and CI. It does not introduce a new native feature version by itself.

## Recovery and rollback

Keep previous signed release APKs outside Git if you need a deployment archive. Android may refuse installing an older version code over a newer build; ordinary sideloading is not an automatic rollback mechanism. Server UI rollback is a separate operation because the page is served remotely.

Loss of the original signing key prevents producing ordinary compatible updates for installations signed by it. The source repository does not contain a recovery copy of that key. Restore the private backup rather than generating a replacement and expecting compatibility.
