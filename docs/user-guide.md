# User guide

[Documentation index](../README.md#documentation)

## The phone and the server

The application gives you an Android home-screen entry for the native Yumina.io service or a custom self-hosted version of Yumina. The native layer remembers which service/server to use and handles the parts that benefit from Android integration: connection recovery, navigation, document selection, saving files, and local login/cache controls.

The selected service/server supplies the main interface and its available features. Changing a story, creating a world, uploading a book where supported, changing the model, or administering an account happens through that interface. Your phone is not the authoritative copy of those records. With native Yumina.io, the hosted service manages the server; with self-hosted Yumina, your installation must remain running and reachable.

## Before installation

Check that the device runs Android 8.0 or newer and has an enabled, working Android WebView provider. For native Yumina.io, connect to the Internet. For a private self-hosted version, use the network or VPN that reaches it. Open the selected address in the phone's browser as a useful network diagnostic. The app uses system/user CAs and can additionally remember a certificate you explicitly accept for a custom HTTPS server. Its saved exceptions do not change the external browser's trust.

Obtain the APK from the app maintainer or your self-hosted server administrator. A custom deployment may offer `/app/android` and serve `yumina-android.apk` with a companion SHA-256 checksum; that installation page is not a requirement for native Yumina.io. Installing an APK is separate from signing in; possession of the APK does not create a service account.

Android asks the installing source, such as the browser, for permission to install applications from that source. Device manufacturers use different wording. This is an Android installation setting rather than an additional application runtime permission.

## First connection

Launch the installed **Yumina OSS Android** application. On a fresh installation the connection screen opens a **Server address** dialog. Enter `https://yumina.io` for the native hosted service or the origin of your custom self-hosted Yumina installation.

| Example | Meaning |
| --- | --- |
| `https://yumina.io` | Native, official Yumina hosted service |
| `https://192.168.1.20` | HTTPS to a server at that LAN address, normally on port 443 |
| `https://stories.example.com` | HTTPS to a server reachable through DNS |
| `http://192.168.1.20:3000` | HTTP to an explicitly exposed application port |
| `http://[fd00::123]:3000` | HTTP to an IPv6 server address with an explicit port |

The examples are placeholders, not the address of your installation. Use the actual reachable address supplied by your server administrator. The native client does not discover servers automatically or assign the server a hostname.

Enter only the server origin. Do not append `/app`, `/app/android`, a chat path, or a query string. Do not include credentials. A trailing `/` is accepted. Surrounding spaces are trimmed. Omitting the scheme selects HTTP, so enter `https://` explicitly for an HTTPS deployment.

The app rejects loopback names/addresses and `0.0.0.0`. On a phone, `localhost` refers to that phone rather than the PC. `0.0.0.0` is a bind address rather than a remote destination.

Select **Connect**. The validated origin is saved and the server's page opens. If the server requires authentication, sign in through its page. Account creation, password resets, roles, and session policy are server responsibilities.

## Daily use

Use the main interface as you would in the server's browser UI. The currently associated server offers discovery, world libraries, story chat, creator tools, OOC and slash commands, model choices, EPUB uploads, and administrative sections for authorized accounts. Features and menu labels can vary with the server build and role.

The native client does not elevate permissions. An administrator sees administrator tools because the server authorizes that account. A restricted account retains its restrictions when using the APK.

An ordinary server web UI update appears after the page reloads. It does not require rebuilding the APK. Native changes such as a revised file export implementation, different certificate handling, or different connection handling require installing a new APK.

## App options

The compatible current web UI includes **App options** in the phone menu, account menu, editor menu, Settings → About, and sign-in/setup pages. Selecting it opens a native dialog. The current server address appears in that dialog's heading.

| Option | Effect |
| --- | --- |
| Server address | Open the address dialog and reconnect to another validated origin |
| Reload page | Reload the page, or retry the origin after a failed connection |
| Open in browser | Open the current same-origin page in an external browser; otherwise use the server origin |
| Clear local login and cache | After confirmation, clear app WebView cookies, web storage, cached pages, and rebuild the WebView |
| About this app | Show the app version and explain server requirements and file support |
| Server certificate | Inspect the certificate accepted for this HTTPS origin or forget it |

Older compatible web UIs that do not advertise the integrated options link get a small native overflow button. When the server cannot load, the native recovery screen still exposes App options and Retry connection.

Changing servers creates a fresh WebView navigation history. It does not intentionally erase the previous server's account or server-side data. Finish or cancel an active file export before changing the server or clearing local login.

Certificate exceptions are managed separately under **Server certificate**. Clearing local login applies to WebView cookies and web storage for all servers used inside this app. It leaves the selected connection address in native preferences and leaves stories/books on the server. A new sign-in may be necessary.

For a private HTTPS server with an untrusted issuer, the app shows the certificate, issuer, validity period and SHA-256 fingerprint. Compare the fingerprint with the administrator through a separate trusted channel, then choose **Trust certificate** to save it for that exact HTTPS origin. Changed certificates require a new decision. Expired, not-yet-valid, or hostname-mismatched certificates cannot be accepted. See [Custom server certificates](custom-server-certificates.md).

## Upload a book or image

Use the relevant upload control in the server UI. The app opens Android's document picker and asks the system to provide the selected document. Choose a file from a provider available on your phone, such as local storage or a configured cloud document provider.

The client translates a `.epub` accept hint to `application/epub+zip`; other extensions can become MIME types through Android's mapping. Multiple selection is supported when the page requests it. Returned items must have `content://` URIs, and the client processes at most 200 selected entries in one callback.

The server decides allowed sizes, valid formats, ownership, extraction behavior, and content processing. The APK's document picker support does not guarantee that the server accepts every chosen file.

## Save an export

A download originating from the configured server can open Android's **Save file** dialog. Choose the name/location, then wait for the completion toast. A successful message is displayed after the destination stream closes successfully.

Two mechanisms are supported:

1. A normal server URL is fetched with the appropriate WebView cookie and streamed into the selected destination. Redirects must remain on the configured origin.
2. A browser-created `blob:` URL from that same origin is read through the loaded page and passed to the save flow. This route has a limit of **10 MiB (10 × 1024 × 1024 bytes)**, described as 10 MB in the UI.

Blob exports larger than that limit should be handled in an external browser if the server UI offers that route. Network exports do not share the Blob byte-size cap, but network timeouts and destination errors can still fail them.

Keep the app and network available until saving finishes. Interrupted writes may leave a partial file at the chosen destination. The application does not automatically remove that document. Inspect or delete an incomplete file using the document provider.

## Back, rotation, and keyboard

The Android back action first gives a compatible web page a chance to handle its own modal/menu navigation. If the page does not handle it, the client uses WebView history, or closes the activity when there is no usable history.

The application handles several configuration changes itself, including orientation and screen size. Insets keep content clear of system bars, cutouts, and the keyboard. Device-specific keyboard and gesture behavior still requires device testing.

## Backgrounding and reconnection

The app flushes cookies and pauses its WebView when backgrounded, then resumes it on return. Android may suspend or terminate the renderer or activity. Story generation is managed by the server; the phone does not keep a separate native inference process running.

After interruption, reopen the chat so the compatible server UI can reconnect to its generation state. Reloading a page does not intentionally submit another user turn. Whether a particular generation survives depends on the server and provider behavior.

## Updates and app identity

The current native version is **1.4.0**, version code **9**, with package ID `io.github.haywoodspartan.yumina.android`. This package installs separately from apps with other package IDs; you can use your existing server account after signing in. An update must use that package ID and the same release signing key to replace an existing installation without resetting its local state.

A debug APK or independently built release with a new signing key cannot replace the existing release in place. Uninstalling removes local app data; server records remain on the server, but local login and address configuration must be recreated.

There is no built-in automatic APK update mechanism. Obtain and install a new APK from the administrator or server download page when native updates are published.
