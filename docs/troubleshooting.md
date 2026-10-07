# Troubleshooting

[Documentation index](../README.md#documentation)

## Server cannot be reached

Use **App options → Server address** and check the actual address/port. Confirm the server is running. Test its address in the phone browser and from another device on the same network. Check Wi-Fi/VPN connectivity, routing, and server firewall settings.

Do not use `localhost`, a loopback address, or `0.0.0.0`. A backend listening only on `127.0.0.1` needs a reachable reverse proxy or another appropriate deployment. The native client does not launch the PC's server.

A change in DHCP-assigned PC address can make an old saved origin stop working. Update the address to the one the phone can reach. A private VPN may use a different reachable address from Wi-Fi.

## The server is taking too long to respond

The main-page load has a 30-second native recovery timer. Check the application and proxy on the PC, then use **Retry connection**. A slow or hung server route is not necessarily a phone fault.

This timer describes initial page navigation. Story generation has its own server/provider timing and is not governed by that timer alone.

## HTTP error shown in the recovery screen

The native screen reports a main-frame HTTP status such as 404 or 503. Check that the configured origin serves the application at its root and that the proxy points to the running backend. Enter the origin without `/app` or a download path.

API-level failures inside a loaded web UI are handled by that server UI. The native app's page error panel does not diagnose every application API response.

## HTTPS certificate cannot be verified

Check the phone's date/time, the exact server hostname/IP, and the certificate served by that endpoint. Verify that the chain is valid for that origin and leads to a configured trust anchor.

No CA certificates are bundled. An unknown private issuer can be accepted through the app's **Trust certificate** dialog after fingerprint review. You can also use an appropriately installed user CA or a publicly trusted server certificate. Renewal of an explicitly accepted private certificate requires a new trust decision.

The app allows only an unknown-issuer exception for a certificate valid for the requested host and current time. It cannot accept date, hostname, or other validation errors. Inspect or remove a saved exception under **App options → Server certificate**. Server-certificate fingerprints and APK signing fingerprints identify different certificates.

## App options cannot be found

Current compatible server builds expose App options in the phone/account/editor menus, Settings → About, and sign-in/setup pages. Older pages without the integrated marker get a native overflow button. The offline recovery screen always offers the menu.

If a custom frontend sets `data-android-app-options="1"` but provides no usable options link, remove the marker or restore the exact `yumina-app://options` user-clickable link. The marker hides the fallback button, so it should advertise an implemented capability.

## New APK will not install over the old app

Check package ID, version code, and signing certificate. Version 1.4.0 uses `io.github.haywoodspartan.yumina.android` and installs separately from apps with another package ID. A fresh standalone build generates a new signing key if none was restored, so it will not replace an app signed by the original private key. Restore `release.p12` and `password.txt` to the intended signing directory and build again.

An Android Studio debug build also has a different identity. A lower version code can be rejected as a downgrade. Uninstalling permits a new identity but removes local login/address state; it does not delete the remote server's stories. Choose that only when the local reset is acceptable.

## Login disappeared or the server changed

Check the current origin in App options. Different origins have separate web storage/session behavior, and the server can expire or revoke sessions. Clearing local login/cache signs out WebView servers but does not remove accounts.

Use the server's own account recovery process. The Android client has no password-reset backend or administrator bypass.

## A book or image cannot be uploaded

Confirm the selected file is supported by the server, below its upload limit, and readable through the chosen Android document provider. The native chooser only returns document access; the server still performs parsing/authorization.

If selection is cancelled, the page receives a null chooser result. Changing servers or replacing the renderer cancels an active upload callback. Retry from the connected same-origin page.

## Export fails or the filename is wrong

Only exports from the configured server origin are supported by the native save flow. A download redirecting to another host/port/scheme is rejected before cookies are forwarded. Use a supported same-origin export route or the browser route offered by the server.

Browser-generated Blob exports are limited to 10 MiB. For larger browser-created output, use **Open in browser** if that frontend can save it there. A direct authenticated network export has a different streaming path.

The save destination may be a local or cloud document provider; quota, connectivity, or stream-closure failures can cause a save error. A partial document can remain in the selected location. Open the provider to inspect/remove it before retrying.

Only one export runs at a time. Finish or cancel it before changing servers, clearing login, or starting another export. A Blob filename is best preserved when the frontend supplies an anchor with a matching href and `download` attribute.

## The app stopped responding or the renderer closed

Retry from the native recovery panel. Renderer replacement creates a fresh WebView and invalidates old callbacks/history. Keep the device's WebView provider updated/enabled and check available device resources.

If a page still fails, use **Open in browser** to determine whether the problem also affects the server UI outside the native client. Include device/WebView/server versions in a bug report.

## A chat changed while the app was backgrounded

The server owns inference and conversation persistence. Reopen that chat and let its UI reconnect. The native app is not a foreground AI service and has no independent transcript store to reconcile against the server.

A page reload does not intentionally resubmit a user turn. Avoid manually submitting duplicate turns while diagnosing a slow server/provider response.

## Portable build reports a checksum mismatch

The locked cache input is missing, corrupt, or different from the expected artifact. Remove only the named cached file and run the builder again. Check access to the download host. Do not change the expected hash simply to suppress the error.

The lock file is deliberately version-specific. Upgrading its tools should be an intentional source change followed by build and device verification.

## Portable build refuses the host

The pinned portable toolchain is Windows x64. Use the conventional Android Studio/Gradle project on other hosts. This repository has no Gradle wrapper, so configure a compatible Gradle/SDK/JDK through your development environment.

## Build says the signing password file is missing

An existing `release.p12` was found without its paired `password.txt`. Restore the original password backup in that signing directory. The builder does not replace an existing key because it cannot open it.

If using `YUMINA_ANDROID_DATA_DIR`, inspect that location rather than the default one. The override selects both cache and signing directories.

## Useful bug-report details

Provide the APK version, Android version, device model, WebView provider/version, compatible server version, approximate failure step, and exact error wording. Say whether the problem reproduces in the phone browser and whether it involves an upload, Blob export, direct download, redirect, or certificate.

Do not include passwords, full session cookies, private keys, signing passwords, model API keys, or sensitive story/account records. A public certificate fingerprint and sanitized server origin can be sufficient for a TLS report.
