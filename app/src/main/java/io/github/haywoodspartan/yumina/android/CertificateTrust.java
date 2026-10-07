package io.github.haywoodspartan.yumina.android;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Locale;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

/** Exact leaf-certificate exceptions, scoped by HTTPS origin. No shared CA bundle. */
public final class CertificateTrust {
    private CertificateTrust() {}

    public static String originKey(String origin) {
        try {
            URI value = new URI(ServerAddress.normalize(origin));
            if (!"https".equals(value.getScheme())) throw new IllegalArgumentException("Certificate trust requires HTTPS.");
            return new URI("https", null, value.getHost(), value.getPort() == -1 ? 443 : value.getPort(), "/", null, null).toASCIIString();
        } catch (java.net.URISyntaxException e) {
            throw new IllegalArgumentException("Invalid HTTPS origin.", e);
        }
    }

    public static X509Certificate decode(byte[] encoded) throws CertificateException {
        if (encoded == null || encoded.length == 0 || encoded.length > 128 * 1024)
            throw new CertificateException("The server certificate is unavailable or too large.");
        ByteArrayInputStream input = new ByteArrayInputStream(encoded);
        X509Certificate result = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(input);
        if (input.available() != 0) throw new CertificateException("Expected one server certificate.");
        return result;
    }

    public static String fingerprint(X509Certificate certificate) throws GeneralSecurityException {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded());
        StringBuilder result = new StringBuilder();
        for (byte value : digest) {
            if (result.length() > 0) result.append(':');
            result.append(String.format(Locale.ROOT, "%02X", value & 0xff));
        }
        return result.toString();
    }

    public static boolean matches(String origin, String requestUrl, X509Certificate trusted, X509Certificate presented) {
        try {
            originKey(origin); // Reject HTTP and malformed configured origins.
            if (!ServerAddress.sameOrigin(origin, requestUrl) || trusted == null || presented == null) return false;
            presented.checkValidity();
            return MessageDigest.isEqual(MessageDigest.getInstance("SHA-256").digest(trusted.getEncoded()),
                    MessageDigest.getInstance("SHA-256").digest(presented.getEncoded()));
        } catch (GeneralSecurityException | IllegalArgumentException e) { return false; }
    }

    /** Apply only to the validated connection. HttpsURLConnection keeps its default hostname verifier. */
    public static SSLSocketFactory socketFactory(String origin, String requestUrl, X509Certificate trusted)
            throws GeneralSecurityException {
        originKey(origin);
        if (!ServerAddress.sameOrigin(origin, requestUrl)) throw new CertificateException("Certificate trust cannot cross origins.");
        TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        factory.init((KeyStore) null);
        for (TrustManager manager : factory.getTrustManagers()) {
            if (manager instanceof X509TrustManager) {
                SSLContext context = SSLContext.getInstance("TLS");
                context.init(null, new TrustManager[]{new ScopedTrustManager((X509TrustManager) manager, trusted)}, null);
                return context.getSocketFactory();
            }
        }
        throw new GeneralSecurityException("No system certificate verifier is available.");
    }

    static final class ScopedTrustManager implements X509TrustManager {
        private final X509TrustManager platform;
        private final X509Certificate trusted;
        ScopedTrustManager(X509TrustManager platform, X509Certificate trusted) {
            this.platform = platform; this.trusted = trusted;
        }
        @Override public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            try { platform.checkServerTrusted(chain, authType); }
            catch (CertificateException original) {
                if (trusted == null || chain == null || chain.length == 0 || chain[0] == null) throw original;
                // The exception covers the saved leaf only; it cannot authorize sibling certificates from a CA.
                for (X509Certificate certificate : chain) {
                    if (certificate == null) throw original;
                    certificate.checkValidity();
                }
                try {
                    if (!MessageDigest.isEqual(MessageDigest.getInstance("SHA-256").digest(trusted.getEncoded()),
                            MessageDigest.getInstance("SHA-256").digest(chain[0].getEncoded()))) throw original;
                } catch (java.security.NoSuchAlgorithmException e) { throw new CertificateException(e); }
            }
        }
        @Override public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            platform.checkClientTrusted(chain, authType);
        }
        @Override public X509Certificate[] getAcceptedIssuers() { return platform.getAcceptedIssuers(); }
    }
}
