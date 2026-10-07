package io.github.haywoodspartan.yumina.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.net.http.SslError;
import android.net.http.SslCertificate;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.webkit.CookieManager;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.SslErrorHandler;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.cert.X509Certificate;
import javax.net.ssl.HttpsURLConnection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONObject;
import org.json.JSONTokener;

/** A thin first-party client. Story data and inference remain on the user's server. */
public final class MainActivity extends Activity {
    private static final int FILE_PICK = 10, FILE_SAVE = 11;
    private static final int BACKGROUND = Color.rgb(18, 19, 22);
    private static final int MAX_EXPORT = 10 * 1024 * 1024;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService transfers = Executors.newSingleThreadExecutor();
    private SharedPreferences preferences, certificatePreferences;
    private AlertDialog certificateDialog;
    private String server = "", pendingDownload, pendingCookie;
    private byte[] pendingBytes;
    private WebView web;
    private FrameLayout content;
    private LinearLayout errorPanel;
    private TextView errorText;
    private ImageButton legacyOptions;
    private ProgressBar progress;
    private ValueCallback<Uri[]> filePicker;
    private boolean pageFailed, destroyed, saving, connected;
    private int navigation;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        preferences = getSharedPreferences("connection", MODE_PRIVATE);
        certificatePreferences = getSharedPreferences("server_certificates", MODE_PRIVATE);
        server = preferences.getString("server", "");
        if (!server.isEmpty()) {
            try { server = ServerAddress.normalize(server); }
            catch (IllegalArgumentException ignored) { server = ""; }
        }
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(BACKGROUND);
        // Android 15/16 enforce edge-to-edge. Keep native controls and the composer
        // outside both system bars and the software keyboard.
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                android.graphics.Insets keyboard = insets.getInsets(WindowInsets.Type.ime());
                v.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, keyboard.bottom));
            } else {
                v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            }
            return Build.VERSION.SDK_INT >= 30 ? WindowInsets.CONSUMED : insets.consumeSystemWindowInsets();
        });
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setVisibility(View.GONE);
        root.addView(progress, new LinearLayout.LayoutParams(-1, dp(2)));
        content = new FrameLayout(this); root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root); root.requestApplyInsets();
        if (Build.VERSION.SDK_INT >= 33) getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::goBack);
        createWebView(); createLegacyOptions(); createErrorPanel();
        if (server.isEmpty()) { showError("Connect to Yumina.io or your own Yumina server to open your library."); showServerDialog(); }
        else if (state != null && server.equals(state.getString("server")) && web.restoreState(state) != null) {
            errorPanel.setVisibility(View.GONE);
        } else connect();
    }

    private void createWebView() {
        web = new WebView(this); web.setBackgroundColor(BACKGROUND);
        web.clearSslPreferences();
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true); settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false); settings.setAllowContentAccess(true);
        settings.setAllowFileAccessFromFileURLs(false); settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setJavaScriptCanOpenWindowsAutomatically(false); settings.setSupportMultipleWindows(false);
        settings.setMediaPlaybackRequiresUserGesture(true); settings.setSafeBrowsingEnabled(true);
        settings.setUserAgentString(settings.getUserAgentString() + " YuminaAndroid/1.4.0");
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, false);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (destroyed || view != web) return true;
                String url = request.getUrl().toString();
                if (connected && ServerAddress.canOpenAppOptions(server, view.getUrl(), url,
                        request.isForMainFrame(), request.hasGesture(), request.isRedirect())) {
                    showMenu(); return true;
                }
                if (ServerAddress.sameOrigin(server, url)) return false;
                if (connected && request.isForMainFrame() && ServerAddress.sameOrigin(server, view.getUrl())
                        && ServerAddress.sameOriginBlob(server, url)) return false;
                if (request.isForMainFrame()) openExternal(url);
                return true;
            }
            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap icon) {
                if (destroyed || view != web) return;
                dismissCertificateDialog();
                navigation++; connected = false; pageFailed = false; progress.setVisibility(View.VISIBLE);
                if (legacyOptions != null) legacyOptions.setVisibility(View.GONE);
                if (!ServerAddress.sameOrigin(server, url)) { view.stopLoading(); showError("This page redirected outside your configured server. Check the server address in App options."); }
                else errorPanel.setVisibility(View.GONE);
                final int epoch = navigation;
                main.postDelayed(() -> {
                    if (!destroyed && view == web && epoch == navigation && !connected && !pageFailed) {
                        view.stopLoading(); showError("Yumina is taking too long to respond. Check your connection and the selected service, then retry.");
                    }
                }, 30000);
            }
            @Override public void onPageFinished(WebView view, String url) {
                if (destroyed || view != web) return;
                progress.setVisibility(View.GONE); CookieManager.getInstance().flush();
                connected = !pageFailed && ServerAddress.sameOrigin(server, url);
                if (connected) {
                    errorPanel.setVisibility(View.GONE);
                    final int epoch = navigation;
                    view.evaluateJavascript("document.documentElement.getAttribute('data-android-app-options')==='1'", integrated -> {
                        if (destroyed || view != web || epoch != navigation || !connected) return;
                        legacyOptions.setVisibility("true".equals(integrated) ? View.GONE : View.VISIBLE);
                        legacyOptions.bringToFront();
                    });
                }
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (destroyed || view != web) return;
                if (request.isForMainFrame()) showError("Couldn't reach Yumina. Check your Internet connection. For a self-hosted server, confirm it is running and reachable through your network or VPN.\n\n" + error.getDescription());
            }
            @Override public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse response) {
                if (destroyed || view != web) return;
                if (request.isForMainFrame()) showError("The server returned HTTP " + response.getStatusCode() + ". Retry or check the selected Yumina service.");
            }
            @Override public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handleCertificateError(view, handler, error);
            }
            @Override public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                if (destroyed || view != web) return true;
                replaceWebView(); showError("The Android web renderer stopped. Tap Retry to restore the app."); return true;
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView view, int value) { if (!destroyed && view == web) progress.setProgress(value); }
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (destroyed || view != web || !connected || !ServerAddress.sameOrigin(server, view.getUrl())) { callback.onReceiveValue(null); return true; }
                if (filePicker != null) filePicker.onReceiveValue(null);
                filePicker = callback;
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
                java.util.ArrayList<String> types = new java.util.ArrayList<>();
                for (String accept : params.getAcceptTypes()) for (String type : accept.split(",")) {
                    type = type.trim();
                    if (type.equalsIgnoreCase(".epub")) type = "application/epub+zip";
                    else if (type.startsWith(".")) type = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(type.substring(1).toLowerCase(java.util.Locale.ROOT));
                    if (type != null && type.contains("/")) types.add(type);
                }
                if (!types.isEmpty()) intent.putExtra(Intent.EXTRA_MIME_TYPES, types.toArray(new String[0]));
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, params.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE);
                try { startActivityForResult(intent, FILE_PICK); }
                catch (ActivityNotFoundException e) { filePicker.onReceiveValue(null); filePicker = null; toast("No document picker is available on this device."); }
                return true;
            }
        });
        web.setDownloadListener((url, userAgent, disposition, mime, length) -> startDownload(url, disposition, mime));
        content.addView(web, 0, new FrameLayout.LayoutParams(-1, -1));
    }

    /** New server/renderer means a new history and no callbacks to the old page. */
    private void replaceWebView() {
        dismissCertificateDialog();
        navigation++; connected = false;
        if (legacyOptions != null) legacyOptions.setVisibility(View.GONE);
        if (filePicker != null) { filePicker.onReceiveValue(null); filePicker = null; }
        if (web != null) {
            WebView previous = web; web = null;
            previous.clearSslPreferences(); content.removeView(previous); previous.destroy();
        }
        createWebView();
        if (errorPanel != null) errorPanel.bringToFront();
    }

    /** Older server UIs still need a way to reach the app's connection settings. */
    private void createLegacyOptions() {
        legacyOptions = new ImageButton(this);
        legacyOptions.setImageResource(R.drawable.app_options);
        legacyOptions.setBackgroundResource(R.drawable.app_options_background);
        legacyOptions.setPadding(dp(12), dp(12), dp(12), dp(12));
        legacyOptions.setContentDescription("App options");
        legacyOptions.setTooltipText("App options");
        legacyOptions.setElevation(dp(3));
        legacyOptions.setOnClickListener(v -> showMenu());
        legacyOptions.setVisibility(View.GONE);
        FrameLayout.LayoutParams layout = new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.BOTTOM | Gravity.END);
        layout.setMargins(dp(12), dp(12), dp(12), dp(12));
        content.addView(legacyOptions, layout);
    }

    private void createErrorPanel() {
        errorPanel = new LinearLayout(this); errorPanel.setOrientation(LinearLayout.VERTICAL);
        errorPanel.setGravity(Gravity.CENTER); errorPanel.setPadding(dp(28), dp(20), dp(28), dp(20));
        errorPanel.setBackgroundColor(BACKGROUND);
        TextView title = new TextView(this); title.setText("Your stories, on your phone"); title.setTextColor(Color.WHITE); title.setTextSize(24);
        errorPanel.addView(title);
        errorText = new TextView(this); errorText.setTextColor(Color.LTGRAY); errorText.setTextSize(16); errorText.setPadding(0, dp(18), 0, dp(18));
        errorPanel.addView(errorText);
        errorPanel.addView(button("Retry connection", this::connect));
        errorPanel.addView(button("App options", this::showMenu));
        content.addView(errorPanel, new FrameLayout.LayoutParams(-1, -1)); errorPanel.setVisibility(View.GONE);
    }

    private void showError(String message) {
        connected = false; pageFailed = true;
        if (legacyOptions != null) legacyOptions.setVisibility(View.GONE);
        if (errorPanel == null) return;
        errorText.setText(message); errorPanel.setVisibility(View.VISIBLE); errorPanel.bringToFront(); progress.setVisibility(View.GONE);
    }
    private void connect() {
        if (server.isEmpty()) { showServerDialog(); return; }
        web.clearSslPreferences();
        pageFailed = false; errorPanel.setVisibility(View.GONE); web.loadUrl(server);
    }

    private X509Certificate trustedCertificate(String origin) {
        try {
            String saved = certificatePreferences.getString(CertificateTrust.originKey(origin), null);
            return saved == null ? null : CertificateTrust.decode(android.util.Base64.decode(saved, android.util.Base64.DEFAULT));
        } catch (Exception ignored) { return null; }
    }

    private String certificateDetails(X509Certificate certificate) throws Exception {
        return "Subject: " + certificate.getSubjectX500Principal().getName()
                + "\nIssuer: " + certificate.getIssuerX500Principal().getName()
                + "\nValid from: " + certificate.getNotBefore() + "\nExpires: " + certificate.getNotAfter()
                + "\n\nSHA-256 fingerprint:\n" + CertificateTrust.fingerprint(certificate);
    }

    private void dismissCertificateDialog() {
        if (certificateDialog != null) { certificateDialog.dismiss(); certificateDialog = null; }
    }

    private void handleCertificateError(WebView view, SslErrorHandler handler, SslError error) {
        if (destroyed || view != web || !ServerAddress.sameOrigin(server, error.getUrl())) { handler.cancel(); return; }
        try {
            if (error.getPrimaryError() != SslError.SSL_UNTRUSTED) throw new IllegalStateException("The certificate has a date, hostname, or other validation error.");
            for (int issue = 0; issue <= SslError.SSL_INVALID; issue++)
                if (issue != SslError.SSL_UNTRUSTED && error.hasError(issue))
                    throw new IllegalStateException("The certificate has a date, hostname, or other validation error.");
            Bundle state = SslCertificate.saveState(error.getCertificate());
            X509Certificate presented = CertificateTrust.decode(state == null ? null : state.getByteArray("x509-certificate"));
            presented.checkValidity();
            X509Certificate saved = trustedCertificate(server);
            if (CertificateTrust.matches(server, error.getUrl(), saved, presented)) { handler.proceed(); return; }
            // Stop the untrusted request first. A confirmed exception takes effect on a fresh connection.
            handler.cancel();
            showError("This server uses a certificate that Android does not trust. Check its fingerprint with your administrator before trusting it for this server.");
            if (certificateDialog != null && certificateDialog.isShowing()) return;
            final String origin = server;
            final int epoch = navigation;
            String message = origin + "\n\n" + (saved == null ? "" : "The previously accepted certificate has changed.\nPrevious SHA-256:\n" + CertificateTrust.fingerprint(saved) + "\n\n")
                    + certificateDetails(presented)
                    + "\n\nCompare this fingerprint with your administrator using a separate trusted channel. Trust applies only to this certificate on this server, not to every certificate from its issuer.";
            certificateDialog = new AlertDialog.Builder(this)
                    .setTitle(saved == null ? "Trust this server certificate?" : "Server certificate changed")
                    .setMessage(message).setNegativeButton("Cancel", null)
                    .setPositiveButton("Trust certificate", (dialog, which) -> {
                        if (destroyed || view != web || navigation != epoch || !ServerAddress.sameOrigin(server, origin)) return;
                        try {
                            presented.checkValidity();
                            certificatePreferences.edit().putString(CertificateTrust.originKey(origin),
                                    android.util.Base64.encodeToString(presented.getEncoded(), android.util.Base64.NO_WRAP)).apply();
                            connect();
                        } catch (Exception e) { showError("Could not save certificate trust: " + e.getMessage()); }
                    }).create();
            certificateDialog.show();
        } catch (Exception e) {
            handler.cancel();
            showError("The HTTPS certificate could not be accepted. " + e.getMessage()
                    + " Check the server address, certificate dates and your phone's clock. Ask the administrator to correct hostname or certificate errors.");
        }
    }

    private void showTrustedCertificate() {
        if (server.isEmpty()) { showServerDialog(); return; }
        if (!server.startsWith("https://")) {
            new AlertDialog.Builder(this).setTitle("Server certificate")
                    .setMessage("This server uses HTTP, which has no certificate or encrypted transport. Choose an HTTPS address to use certificate trust.")
                    .setPositiveButton("Close", null).show(); return;
        }
        X509Certificate saved = trustedCertificate(server);
        try {
            AlertDialog.Builder dialog = new AlertDialog.Builder(this).setTitle("Server certificate")
                    .setMessage(server + "\n\n" + (saved == null
                            ? "No certificate exception is saved for this server. Android's normal system and user CA trust applies. When connecting to a custom server with an unknown issuer, the app offers a fingerprint-based trust choice."
                            : certificateDetails(saved))).setPositiveButton("Close", null);
            if (saved != null) dialog.setNegativeButton("Forget certificate", (d, which) -> {
                if (saving) { toast("Finish or cancel the current file export first."); return; }
                certificatePreferences.edit().remove(CertificateTrust.originKey(server)).apply();
                replaceWebView(); connect();
            });
            dialog.show();
        } catch (Exception e) { toast("Could not read certificate details: " + e.getMessage()); }
    }
    private void showServerDialog() {
        LinearLayout form = new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL); form.setPadding(dp(24), dp(8), dp(24), 0);
        TextView help = new TextView(this); help.setText("Use the native Yumina.io service or enter the address of a custom self-hosted Yumina server. Private servers may require the same Wi-Fi network or a VPN.\n\nExample: https://yumina.io"); form.addView(help);
        EditText input = new EditText(this); input.setSingleLine(true); input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        input.setHint("https://yumina.io"); input.setText(server); input.setSelectAllOnFocus(true); form.addView(input);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Server address").setView(form)
                .setNegativeButton("Cancel", null).setPositiveButton("Connect", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                String next = ServerAddress.normalize(input.getText().toString());
                if (saving) { input.setError("Finish or cancel the current file export first."); return; }
                server = next; preferences.edit().putString("server", server).apply();
                replaceWebView(); dialog.dismiss(); connect();
            } catch (IllegalArgumentException e) { input.setError(e.getMessage()); }
        })); dialog.show();
    }

    private void showMenu() {
        LinearLayout heading = new LinearLayout(this); heading.setOrientation(LinearLayout.VERTICAL);
        heading.setPadding(dp(24), dp(24), dp(24), dp(12));
        TextView title = new TextView(this); title.setText("App options"); title.setTextColor(Color.WHITE); title.setTextSize(22);
        heading.addView(title);
        TextView connection = new TextView(this);
        connection.setText(server.isEmpty() ? "No server connected" : server);
        connection.setTextColor(Color.LTGRAY); connection.setTextSize(13); connection.setPadding(0, dp(6), 0, 0);
        heading.addView(connection);
        new AlertDialog.Builder(this).setCustomTitle(heading)
            .setItems(new String[]{"Server address", "Reload page", "Open in browser", "Clear local login and cache", "About this app", "Server certificate"}, (dialog, which) -> {
                if (which == 0) showServerDialog();
                if (which == 1) { if (server.isEmpty()) showServerDialog(); else if (pageFailed) connect(); else { web.clearSslPreferences(); web.reload(); } }
                if (which == 5) showTrustedCertificate();
                if (which == 2 && !server.isEmpty()) openExternal(ServerAddress.sameOrigin(server, web.getUrl()) ? web.getUrl() : server);
                if (which == 3) new AlertDialog.Builder(this).setTitle("Clear this phone's login?")
                    .setMessage("This signs out all servers used in this app and clears cached pages and local preferences from the web UI. Your stories and books stay on the server.")
                    .setNegativeButton("Cancel", null).setPositiveButton("Clear", (d, w) -> {
                        if (saving) { toast("Finish or cancel the current file export first."); return; }
                        web.stopLoading(); connected = false; navigation++;
                        CookieManager.getInstance().removeAllCookies(done -> {
                            if (destroyed || web == null) return;
                            CookieManager.getInstance().flush(); android.webkit.WebStorage.getInstance().deleteAllData();
                            web.clearCache(true); replaceWebView(); connect();
                        });
                    }).show();
                if (which == 4) new AlertDialog.Builder(this).setTitle("Your server, your stories")
                    .setMessage("Yumina OSS Android · 1.4.0\n\nWorks with the native Yumina.io service or a custom self-hosted version of Yumina. Explore worlds, chat, and use the features available on your chosen service. Accounts, memories, and model inference stay with that service. Self-hosted servers must remain running and reachable.\n\nChoose files from Android's document picker for supported uploads. Exports open a Save file dialog. The app does not run models offline.\n\nAfter a network interruption, return to the chat to reconnect to server generation. Reload never intentionally submits another turn.")
                    .setPositiveButton("OK", null).show();
            }).show();
    }

    private void openExternal(String url) {
        if (url == null) return;
        Uri uri = Uri.parse(url); String scheme = uri.getScheme();
        if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme) && !"mailto".equalsIgnoreCase(scheme)) {
            toast("This link type is not supported."); return;
        }
        try { startActivity(new Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)); }
        catch (ActivityNotFoundException e) { toast("No app is available to open this link."); }
    }

    private void startDownload(String url, String disposition, String mime) {
        if (!connected || saving || !ServerAddress.sameOrigin(server, web.getUrl())) { toast("Connect to the server and finish the current export first."); return; }
        saving = true;
        String type = mime == null || mime.isEmpty() ? "application/octet-stream" : mime;
        String filename = URLUtil.guessFileName(url, disposition, type);
        if (ServerAddress.sameOriginBlob(server, url)) {
            // Pull a bounded Blob from our own page using evaluateJavascript; no
            // addJavascriptInterface or unrestricted native bridge is exposed.
            final int epoch = navigation;
            String key = "__yuminaExport" + System.nanoTime();
            String script = "(()=>{const k=" + JSONObject.quote(key) + ";const u=" + JSONObject.quote(url)
                + ";const a=Array.from(document.querySelectorAll('a[download]')).find(a=>a.href===u);const name=a?a.download:'';window[k]={pending:true};fetch(u"
                + ").then(r=>r.blob()).then(b=>{if(b.size>" + MAX_EXPORT + ")throw Error('Export exceeds 10 MB');"
                + "const f=new FileReader();f.onload=()=>window[k]={data:f.result,name};f.onerror=()=>window[k]={error:'Could not read export'};f.readAsDataURL(b)})"
                + ".catch(e=>window[k]={error:String(e)});return true})()";
            web.evaluateJavascript(script, ignored -> pollBlob(key, epoch, 0, filename, type));
        } else if (ServerAddress.sameOrigin(server, url)) {
            pendingDownload = url; pendingCookie = CookieManager.getInstance().getCookie(url); savePicker(filename, type);
        } else { saving = false; toast("Only exports from your configured server can be saved here."); }
    }

    private void pollBlob(String key, int epoch, int attempts, String filename, String mime) {
        if (destroyed || web == null || navigation != epoch || attempts > 100) { clearDownload(); return; }
        web.evaluateJavascript("JSON.stringify(window[" + JSONObject.quote(key) + "]||{error:'Export expired'})", raw -> {
            if (destroyed || navigation != epoch) { clearDownload(); return; }
            try {
                Object decoded = new JSONTokener(raw).nextValue();
                JSONObject result = new JSONObject((String) decoded);
                if (result.optBoolean("pending")) { main.postDelayed(() -> pollBlob(key, epoch, attempts + 1, filename, mime), 100); return; }
                web.evaluateJavascript("delete window[" + JSONObject.quote(key) + "]", null);
                if (result.has("error")) throw new IllegalStateException(result.getString("error"));
                String data = result.getString("data");
                if (data.length() > MAX_EXPORT * 4 / 3 + 1024) throw new IllegalStateException("Export exceeds 10 MB");
                pendingBytes = android.util.Base64.decode(data.substring(data.indexOf(',') + 1), android.util.Base64.DEFAULT);
                savePicker(result.optString("name", "").isEmpty() ? filename : result.getString("name"), mime);
            } catch (Exception e) { clearDownload(); toast("Export failed: " + e.getMessage()); }
        });
    }

    private void savePicker(String filename, String mime) {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(mime)
                .putExtra(Intent.EXTRA_TITLE, filename.replaceAll("[\\\\/\\r\\n]", "_"));
        try { startActivityForResult(intent, FILE_SAVE); }
        catch (ActivityNotFoundException e) { clearDownload(); toast("No document picker is available."); }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == FILE_PICK && filePicker != null) {
            Uri[] files = null;
            if (result == RESULT_OK && data != null) {
                if (data.getClipData() != null) {
                    int count = Math.min(data.getClipData().getItemCount(), 200); files = new Uri[count];
                    for (int i = 0; i < count; i++) files[i] = data.getClipData().getItemAt(i).getUri();
                } else if (data.getData() != null) files = new Uri[]{data.getData()};
                if (files != null) for (Uri uri : files) if (!"content".equals(uri.getScheme())) { files = null; break; }
            }
            filePicker.onReceiveValue(files); filePicker = null;
        } else if (request == FILE_SAVE) {
            if (result != RESULT_OK || data == null || data.getData() == null || (pendingBytes == null && pendingDownload == null)) { clearDownload(); return; }
            final Uri destination = data.getData(); final String source = pendingDownload, cookie = pendingCookie, origin = server;
            final byte[] bytes = pendingBytes;
            transfers.execute(() -> {
                HttpURLConnection connection = null;
                try {
                  try (OutputStream output = getContentResolver().openOutputStream(destination, "wt")) {
                    if (output == null) throw new IllegalStateException("Cannot write to this location");
                    if (bytes != null) output.write(bytes);
                    else {
                        String next = source;
                        for (int redirects = 0; redirects < 6; redirects++) {
                            if (!ServerAddress.sameOrigin(origin, next)) throw new IllegalStateException("Download redirected outside this server");
                            connection = (HttpURLConnection) new URL(next).openConnection();
                            X509Certificate trusted = trustedCertificate(origin);
                            if (connection instanceof HttpsURLConnection && trusted != null)
                                ((HttpsURLConnection) connection).setSSLSocketFactory(CertificateTrust.socketFactory(origin, next, trusted));
                            connection.setInstanceFollowRedirects(false); connection.setConnectTimeout(15000); connection.setReadTimeout(30000);
                            if (cookie != null) connection.setRequestProperty("Cookie", cookie);
                            int status = connection.getResponseCode();
                            if (status >= 300 && status < 400) {
                                String location = connection.getHeaderField("Location");
                                if (location == null) throw new IllegalStateException("Redirect without a location");
                                next = new URL(new URL(next), location).toString(); connection.disconnect(); connection = null; continue;
                            }
                            if (status != 200) throw new IllegalStateException("Server returned HTTP " + status);
                            break;
                        }
                        if (connection == null) throw new IllegalStateException("Too many redirects");
                        try (InputStream input = connection.getInputStream()) {
                            byte[] buffer = new byte[32768]; int read;
                            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
                        }
                    }
                  }
                  // Closing the provider's stream can fail. Only report success
                  // after the file has been flushed and closed successfully.
                  main.post(() -> { clearDownload(); toast("File saved."); });
                } catch (Exception e) { main.post(() -> { clearDownload(); toast("Save failed: " + e.getMessage()); }); }
                finally { if (connection != null) connection.disconnect(); }
            });
        }
    }

    private void clearDownload() { saving = false; pendingBytes = null; pendingDownload = null; pendingCookie = null; }
    private void goBack() {
        if (web != null && connected && ServerAddress.sameOrigin(server, web.getUrl())) {
            final int epoch = navigation;
            web.evaluateJavascript("typeof window.yuminaAndroidBack==='function' && window.yuminaAndroidBack()", handled -> {
                if (!destroyed && epoch == navigation && !"true".equals(handled)) backOrFinish();
            });
        } else backOrFinish();
    }
    private void backOrFinish() { if (web != null && !pageFailed && web.canGoBack()) web.goBack(); else finish(); }
    @Override public void onBackPressed() { goBack(); }
    @Override protected void onSaveInstanceState(Bundle state) { super.onSaveInstanceState(state); state.putString("server", server); if (web != null) web.saveState(state); }
    @Override protected void onPause() { super.onPause(); CookieManager.getInstance().flush(); if (web != null) web.onPause(); }
    @Override protected void onResume() { super.onResume(); if (web != null) web.onResume(); }
    @Override protected void onDestroy() {
        destroyed = true; main.removeCallbacksAndMessages(null);
        dismissCertificateDialog();
        if (filePicker != null) { filePicker.onReceiveValue(null); filePicker = null; }
        if (web != null) { content.removeView(web); web.destroy(); web = null; }
        transfers.shutdown(); super.onDestroy();
    }
    private Button button(String label, Runnable action) { Button b = new Button(this); b.setText(label); b.setAllCaps(false); b.setOnClickListener(v -> action.run()); return b; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void toast(String text) { if (!destroyed) Toast.makeText(this, text, Toast.LENGTH_LONG).show(); }
}
