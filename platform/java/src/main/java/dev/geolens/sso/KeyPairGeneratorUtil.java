package dev.geolens.sso;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemWriter;

import java.io.StringWriter;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Date;

/**
 * SAML imzalama sertifikası üretici — Go {@code GenerateKeyPair} karşılığı (K1).
 * <p>RSA 2048 anahtar çifti + 1 yıl geçerli, "GeoLens / SAML Signing Certificate"
 * konulu self-signed X.509 sertifika üretir (Go {@code crypto/x509} davranışı birebir).
 */
public final class KeyPairGeneratorUtil {

    private KeyPairGeneratorUtil() {
    }

    /** Üretilen anahtar çifti + sertifika (PEM). */
    public record GeneratedKeys(String certificatePem, String privateKeyPem) {
    }

    public static GeneratedKeys generate() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(2048);
            KeyPair kp = kpg.generateKeyPair();

            long nowMillis = System.currentTimeMillis();
            X500Name subject = new X500Name("O=GeoLens, CN=SAML Signing Certificate");
            BigInteger serial = BigInteger.valueOf(nowMillis);

            X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                    subject, serial,
                    new Date(nowMillis),
                    new Date(nowMillis + 365L * 24 * 3600 * 1000),
                    subject, kp.getPublic());

            ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").build(kp.getPrivate());
            X509Certificate cert = new JcaX509CertificateConverter().getCertificate(builder.build(signer));

            return new GeneratedKeys(pem("CERTIFICATE", cert.getEncoded()), pem("RSA PRIVATE KEY", kp.getPrivate().getEncoded()));
        } catch (Exception e) {
            throw new IllegalStateException("anahtar oluşturulamadı", e);
        }
    }

    private static String pem(String type, byte[] der) throws Exception {
        StringWriter sw = new StringWriter();
        PemWriter pw = new PemWriter(sw);
        pw.writeObject(new PemObject(type, der));
        pw.close();
        return sw.toString();
    }
}
