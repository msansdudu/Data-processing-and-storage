package ru.nsu.chebotareva.common;

import org.bouncycastle.openssl.jcajce.JcaPEMWriter;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

public class PemUtil {
    public static byte[] privateKeyToPemBytes(PrivateKey key) throws IOException {
        StringWriter sw = new StringWriter();
        try (JcaPEMWriter w = new JcaPEMWriter(sw)) {
            w.writeObject(key);
        }
        return sw.toString().getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] certificateToPemBytes(X509Certificate cert) throws IOException {
        StringWriter sw = new StringWriter();
        try (JcaPEMWriter w = new JcaPEMWriter(sw)) {
            w.writeObject(cert);
        }
        return sw.toString().getBytes(StandardCharsets.UTF_8);
    }
}
