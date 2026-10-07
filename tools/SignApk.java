import com.android.apksig.ApkSigner;
import com.android.apksig.ApkVerifier;
import java.io.File;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Collections;

/** Build-only utility: the key and its password are never packaged in the APK. */
public final class SignApk {
    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("keystore input.apk output.apk");
        char[] password = System.getenv("YUMINA_ANDROID_SIGNING_PASSWORD").toCharArray();
        KeyStore store = KeyStore.getInstance("PKCS12");
        try (FileInputStream input = new FileInputStream(args[0])) { store.load(input, password); }
        PrivateKey key = (PrivateKey) store.getKey("yumina-android", password);
        X509Certificate certificate = (X509Certificate) store.getCertificate("yumina-android");
        ApkSigner.SignerConfig config = new ApkSigner.SignerConfig.Builder("yumina-android", key, Collections.singletonList(certificate)).build();
        new ApkSigner.Builder(Collections.singletonList(config)).setInputApk(new File(args[1])).setOutputApk(new File(args[2]))
                .setMinSdkVersion(26).setV1SigningEnabled(false).setV2SigningEnabled(true).setV3SigningEnabled(true).setV4SigningEnabled(false).build().sign();
        ApkVerifier.Result result = new ApkVerifier.Builder(new File(args[2])).setMinCheckedPlatformVersion(26).build().verify();
        if (!result.isVerified() || !result.isVerifiedUsingV2Scheme() || !result.isVerifiedUsingV3Scheme())
            throw new IllegalStateException("APK signature verification failed: " + result.getErrors());
        byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded());
        StringBuilder hex = new StringBuilder(); for (byte b : digest) hex.append(String.format("%02x", b));
        System.out.println("Verified APK v2 + v3 signature. Certificate SHA-256: " + hex);
    }
}
