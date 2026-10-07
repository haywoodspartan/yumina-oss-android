package io.github.haywoodspartan.yumina.android;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.net.ssl.X509TrustManager;

/** Real, temporary X.509 fixtures exercise exact-certificate and expiry boundaries. */
public final class CertificateTrustTest {
    private static int checks;
    private static final String ORIGIN = "https://private.example/";
    private static void check(boolean result, String message) { checks++; if (!result) throw new AssertionError(message); }
    private interface Attempt { void run() throws Exception; }
    private static void reject(Attempt action, String message) throws Exception {
        try { action.run(); throw new AssertionError("Accepted " + message); }
        catch (java.security.GeneralSecurityException | IllegalArgumentException expected) { checks++; }
    }

    private static void generate(Path store, String alias, String start) throws Exception {
        String tool = System.getProperty("os.name").startsWith("Windows") ? "keytool.exe" : "keytool";
        List<String> command = new ArrayList<>(Arrays.asList(Paths.get(System.getProperty("java.home"), "bin", tool).toString(),
                "-genkeypair", "-keystore", store.toString(), "-storetype", "PKCS12", "-storepass", "test-only-password",
                "-alias", alias, "-keyalg", "RSA", "-keysize", "2048", "-validity", start == null ? "365" : "1",
                "-dname", "CN=private.example", "-ext", "SAN=dns:private.example", "-noprompt"));
        if (start != null) command.addAll(Arrays.asList("-startdate", start));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (InputStream stream = process.getInputStream()) {
            byte[] buffer = new byte[4096]; int count;
            while ((count = stream.read(buffer)) != -1) output.write(buffer, 0, count);
        }
        if (process.waitFor() != 0) throw new IllegalStateException(output.toString("UTF-8"));
    }

    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("yumina-certificate-tests-");
        Path storePath = directory.resolve("fixtures.p12");
        try {
            generate(storePath, "accepted", null);
            generate(storePath, "replacement", null);
            generate(storePath, "expired", "2020/01/01 00:00:00");
            generate(storePath, "future", "2099/01/01 00:00:00");
            KeyStore store = KeyStore.getInstance("PKCS12");
            try (InputStream input = Files.newInputStream(storePath)) { store.load(input, "test-only-password".toCharArray()); }
            X509Certificate accepted = (X509Certificate) store.getCertificate("accepted");
            X509Certificate replacement = (X509Certificate) store.getCertificate("replacement");
            X509Certificate expired = (X509Certificate) store.getCertificate("expired");
            X509Certificate future = (X509Certificate) store.getCertificate("future");
            check(CertificateTrust.originKey(ORIGIN).equals(CertificateTrust.originKey("HTTPS://PRIVATE.EXAMPLE:443")), "Canonical default port/case");
            check(!CertificateTrust.originKey(ORIGIN).equals(CertificateTrust.originKey("https://private.example:8443")), "Separate ports");
            check(CertificateTrust.originKey("https://[fd00::123]").equals("https://[fd00::123]:443/"), "IPv6 origin key");
            reject(() -> CertificateTrust.originKey("http://private.example"), "cleartext trust");
            reject(() -> CertificateTrust.originKey("https://private.example/path"), "path in stored origin");
            check(CertificateTrust.matches(ORIGIN, "https://PRIVATE.example:443/export", accepted, accepted), "Explicitly accepted same-origin leaf");
            for (String url : new String[]{"https://elsewhere.example/", "https://private.example:8443/", "http://private.example/", "https://private.example.evil/", "https://user@private.example/", null})
                check(!CertificateTrust.matches(ORIGIN, url, accepted, accepted), "No cross-origin trust: " + url);
            check(!CertificateTrust.matches("http://private.example/", "http://private.example/export", accepted, accepted), "No HTTP exception");
            check(!CertificateTrust.matches(ORIGIN, ORIGIN, null, accepted), "No saved exception");
            check(!CertificateTrust.matches(ORIGIN, ORIGIN, accepted, null), "No presented certificate");
            check(!CertificateTrust.matches(ORIGIN, ORIGIN, accepted, replacement), "Changed certificate is not silently accepted");
            check(!CertificateTrust.matches(ORIGIN, ORIGIN, expired, expired), "Expired pin cannot authorize a page");
            check(!CertificateTrust.matches(ORIGIN, ORIGIN, future, future), "Future pin cannot authorize a page");
            X509Certificate restored = CertificateTrust.decode(accepted.getEncoded());
            check(CertificateTrust.fingerprint(restored).equals(CertificateTrust.fingerprint(accepted)), "DER persistence round trip");
            check(CertificateTrust.fingerprint(restored).matches("[0-9A-F]{2}(:[0-9A-F]{2}){31}"), "SHA-256 display format");
            reject(() -> CertificateTrust.decode(null), "missing DER");
            reject(() -> CertificateTrust.decode(new byte[]{1, 2, 3}), "invalid DER");
            reject(() -> CertificateTrust.decode(new byte[128 * 1024 + 1]), "oversized DER");
            reject(() -> CertificateTrust.decode(Arrays.copyOf(accepted.getEncoded(), accepted.getEncoded().length + 1)), "trailing DER data");
            check(CertificateTrust.socketFactory(ORIGIN, ORIGIN + "export", accepted) != null, "Scoped download TLS factory");
            reject(() -> CertificateTrust.socketFactory(ORIGIN, "https://elsewhere.example/file", accepted), "redirect to a different host");
            reject(() -> CertificateTrust.socketFactory(ORIGIN, "https://private.example:8443/file", accepted), "redirect to a different port");
            reject(() -> CertificateTrust.socketFactory(ORIGIN, "http://private.example/file", accepted), "redirect to HTTP");

            X509TrustManager untrustedPlatform = new X509TrustManager() {
                public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException { throw new CertificateException("Unknown issuer"); }
                public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException { throw new CertificateException("No client certificates"); }
                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            };
            CertificateTrust.ScopedTrustManager pinned = new CertificateTrust.ScopedTrustManager(untrustedPlatform, accepted);
            pinned.checkServerTrusted(new X509Certificate[]{accepted}, "RSA"); checks++;
            reject(() -> pinned.checkServerTrusted(new X509Certificate[]{replacement}, "RSA"), "replacement certificate on download");
            reject(() -> pinned.checkServerTrusted(new X509Certificate[]{accepted, expired}, "RSA"), "expired certificate in chain");
            reject(() -> pinned.checkServerTrusted(new X509Certificate[]{accepted, null}, "RSA"), "null certificate in chain");
            reject(() -> pinned.checkServerTrusted(new X509Certificate[0], "RSA"), "empty chain");
            reject(() -> pinned.checkServerTrusted(null, "RSA"), "missing chain");
            reject(() -> pinned.checkClientTrusted(new X509Certificate[]{accepted}, "RSA"), "server pin as client authorization");
            reject(() -> new CertificateTrust.ScopedTrustManager(untrustedPlatform, null).checkServerTrusted(new X509Certificate[]{accepted}, "RSA"), "removed saved exception");
            reject(() -> new CertificateTrust.ScopedTrustManager(untrustedPlatform, expired).checkServerTrusted(new X509Certificate[]{expired}, "RSA"), "expired download leaf");
            reject(() -> new CertificateTrust.ScopedTrustManager(untrustedPlatform, future).checkServerTrusted(new X509Certificate[]{future}, "RSA"), "not-yet-valid download leaf");
            X509TrustManager normalPlatform = new X509TrustManager() {
                public void checkServerTrusted(X509Certificate[] chain, String authType) {}
                public void checkClientTrusted(X509Certificate[] chain, String authType) {}
                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[]{replacement}; }
            };
            CertificateTrust.ScopedTrustManager normal = new CertificateTrust.ScopedTrustManager(normalPlatform, accepted);
            normal.checkServerTrusted(new X509Certificate[]{replacement}, "RSA"); checks++;
            check(normal.getAcceptedIssuers()[0] == replacement, "Platform CA behavior is retained");
            System.out.println(checks + " certificate trust checks passed.");
        } finally {
            Files.deleteIfExists(storePath);
            Files.deleteIfExists(directory);
        }
    }
}
