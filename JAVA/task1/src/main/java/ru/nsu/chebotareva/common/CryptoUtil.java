package ru.nsu.chebotareva.common;

import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.util.io.pem.PemObject;

import javax.security.auth.x500.X500Principal;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.math.BigInteger;
import java.util.Date;

public class CryptoUtil {
    private static volatile boolean bc;

    private static void ensureBC() {
        if (!bc) {
            synchronized (CryptoUtil.class) {
                if (!bc) {
                    if (Security.getProvider("BC") == null) {
                        Security.addProvider(new BouncyCastleProvider());
                    }
                    bc = true;
                }
            }
        }
    }

    public static PrivateKey loadPrivateKeyFromPem(Path pemPath) throws IOException {
        ensureBC();
        try (Reader r = Files.newBufferedReader(pemPath, StandardCharsets.UTF_8);
             PEMParser parser = new PEMParser(r)) {
            Object obj = parser.readObject();
            if (obj == null) throw new IOException("Empty PEM file: " + pemPath);
            JcaPEMKeyConverter conv = new JcaPEMKeyConverter().setProvider("BC");
            if (obj instanceof PrivateKeyInfo pki) {
                return conv.getPrivateKey(pki);
            } else if (obj instanceof PEMKeyPair kp) {
                KeyPair k = conv.getKeyPair(kp);
                return k.getPrivate();
            } else if (obj instanceof PemObject po) {
                try (PEMParser p2 = new PEMParser(new BufferedReader(Files.newBufferedReader(pemPath, StandardCharsets.UTF_8)))) {
                    Object again = p2.readObject();
                    if (again instanceof PrivateKeyInfo pki2) return conv.getPrivateKey(pki2);
                    if (again instanceof PEMKeyPair kp2) return conv.getKeyPair(kp2).getPrivate();
                }
                throw new IOException("Unsupported PEM content: " + po.getType());
            } else {
                throw new IOException("Unsupported PEM content: " + obj.getClass());
            }
        }
    }

    public static KeyPair generateRsa8192() throws NoSuchAlgorithmException {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(8192, new SecureRandom());
        return kpg.generateKeyPair();
    }

    public static X509Certificate issueCertificate(String issuerDn, String subjectCn, PublicKey subjectPublicKey, PrivateKey issuerPrivateKey)
            throws OperatorCreationException, CertificateException {
        ensureBC();
        X500Principal issuer = new X500Principal(issuerDn);
        X500Principal subject = new X500Principal("CN=" + subjectCn);
        Date notBefore = new Date(System.currentTimeMillis() - 5 * 60 * 1000);
        Date notAfter = new Date(System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000);
        BigInteger serial = new BigInteger(160, new SecureRandom());
        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                issuer,
                serial,
                notBefore,
                notAfter,
                subject,
                subjectPublicKey
        );
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").setProvider("BC").build(issuerPrivateKey);
        X509CertificateHolder holder = builder.build(signer);
        return new JcaX509CertificateConverter().setProvider("BC").getCertificate(holder);
    }
}
