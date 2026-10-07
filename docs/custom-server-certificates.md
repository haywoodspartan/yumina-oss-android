# Custom server certificates

[Documentation index](../README.md#documentation)

## What the app accepts

Yumina OSS Android ships without any bundled CA certificates. Official Yumina.io and other normally trusted HTTPS services use Android's system/user certificate trust. For private infrastructure, the app can remember an explicit exception for the server certificate presented by a self-signed server or a server whose issuing CA is not trusted by Android.

The exception is tied to the **exact leaf certificate and HTTPS origin**: scheme, hostname/IP, and effective port. It does not add the issuer to a global CA store or approve every other certificate issued by that CA. This lets a local or privately hosted Yumina instance work without installing its CA device-wide.

## Connect to a custom HTTPS server

1. Obtain the server's HTTPS address and its current **leaf certificate SHA-256 fingerprint** from the administrator through a separate trusted channel.
2. Open **App options → Server address**, enter the HTTPS origin, and connect.
3. If the only TLS problem is an unknown issuer, the app stops the request and shows a certificate dialog.
4. Review the origin, subject, issuer, validity dates, and SHA-256 fingerprint. Compare the displayed fingerprint with the administrator's value.
5. Choose **Trust certificate** to remember it for this origin, or **Cancel** to leave the request blocked.
6. After acceptance the app starts a fresh connection. It can then accept the saved certificate on future connections to that same origin.

There is no universal “ignore HTTPS errors” switch. An expired, not-yet-valid, hostname-mismatched, or otherwise invalid certificate cannot be accepted through this dialog. The server administrator must correct those issues.

## Which fingerprint is needed?

Use the SHA-256 fingerprint of the **server's leaf certificate**, the certificate served for the address entered in the app. This is different from the issuing root CA fingerprint and different from the certificate used to sign the Android APK.

An administrator with OpenSSL and the public server certificate file can display it with:

```sh
openssl x509 -in server-certificate.pem -noout -fingerprint -sha256
```

The certificate file contains public information. Private key files must remain private. Supply the fingerprint through a channel the user already trusts; a fingerprint shown only by the same unexpected network connection is not an independent identity check.

## Hostnames, IP addresses, and ports

The certificate must be valid for the entered hostname or IP address. Use subject alternative names covering the actual client address. For example, a certificate issued only for `yumina.internal.example` should be reached through that hostname, not an unrelated IP address.

Default HTTPS port spelling is normalized: `https://yumina.internal.example` and `https://yumina.internal.example:443` share a saved trust entry. Port 8443 is a different origin and needs its own choice. Another hostname, even if it reaches the same machine, is also a different origin.

HTTP addresses have no TLS certificate. To use this feature, configure HTTPS on the server and enter its HTTPS origin.

## Review and forget a certificate

Open **App options → Server certificate**. The dialog shows the saved exception for the selected server, including its fingerprint and dates. If no exception is saved, it explains that normal Android trust applies.

Choose **Forget certificate** to remove the exception. The app clears cached WebView SSL decisions, replaces the WebView, and reconnects. A server still using an unknown issuer then requires a new explicit decision. Finish or cancel an active file export before forgetting its certificate.

Trust choices are separate from **Clear local login and cache**. Clearing login removes web sessions/storage; it does not remove an explicitly accepted certificate. Uninstalling or clearing application data removes saved certificate choices along with the app's other private preferences.

## Certificate renewal or replacement

An exception matches the complete certificate, so even a routine renewal with the same key produces a different fingerprint. A replacement with an unknown issuer opens a **Server certificate changed** dialog showing the previously accepted fingerprint and the new certificate details.

The new certificate is not silently approved. Compare the new fingerprint with the administrator before choosing **Trust certificate** again. Choosing Cancel keeps the previous saved entry but leaves the presented replacement blocked.

A replacement that is normally valid under Android's system/user CA trust can use normal platform verification without a saved exception. The optional exception supplements ordinary trust; it does not impose exclusive pinning on all normally trusted certificates.

## File downloads and external browsers

The saved exception also applies to native same-origin HTTPS exports. The downloader retains Android's normal hostname verifier and checks every redirect before forwarding the server cookie. A redirect to another host, scheme, or port is refused by the native save flow.

Browser-generated Blob exports continue to use the current trusted page and the 10 MiB limit. An external browser does not inherit this app's saved exceptions. If you use **Open in browser**, that browser applies its own certificate trust and may require a separate deployment solution.

## Storage and scope

Accepted public certificates are stored in the app's private `server_certificates` preferences, keyed by canonical HTTPS origin. They do not contain private keys or passwords and are not included in future APK builds. The manifest disables Android app backup.

The app offers acceptance only for the selected server's origin. It does not offer a trust prompt for an unrelated third-party resource. Android's `SslError` flags are checked so an unknown issuer cannot hide another reported hostname/date/validation error.

## Troubleshooting

| Situation | What to check |
| --- | --- |
| No trust option appears | Check that the URL is HTTPS and that the problem is an unknown issuer, not a date/hostname/handshake failure |
| Certificate details cannot be read | The app fails closed when it cannot decode the platform's presented X.509 certificate |
| Hostname error | Enter a name covered by the certificate or have the administrator reissue it with the needed SAN |
| Expired or future certificate | Correct server certificate dates and check the phone's clock |
| Trust prompt returns after renewal | Compare and accept the new leaf fingerprint; renewals are distinct certificates |
| One server works but another port does not | Certificate exceptions are scoped to the full origin, including port |
| Browser still rejects the service | App-local trust does not change another app's trust settings |
| Download fails after a server change | Reconnect and review the new certificate before exporting again; cross-origin redirects remain blocked |

The automated suite covers the certificate policy and download verifier. Android WebView prompts and actual device networking are part of the [device smoke test](releasing-and-testing.md#device-smoke-test).
