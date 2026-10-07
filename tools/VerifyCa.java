import java.io.FileInputStream;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

/** Checks the public trust anchor before packaging; never reads a private key. */
public final class VerifyCa {
    public static void main(String[] args) throws Exception {
        X509Certificate ca;
        try (FileInputStream input = new FileInputStream(args[0])) {
            ca = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(input);
        }
        ca.checkValidity();
        if (ca.getBasicConstraints() < 0 || !ca.getSubjectX500Principal().equals(ca.getIssuerX500Principal()))
            throw new IllegalStateException("Expected a CA root certificate");
        boolean[] usage = ca.getKeyUsage();
        if (usage != null && (usage.length <= 5 || !usage[5])) throw new IllegalStateException("CA lacks certificate signing usage");
        ca.verify(ca.getPublicKey());
        System.out.println("Verified bundled public CA: " + ca.getSubjectX500Principal());
    }
}
