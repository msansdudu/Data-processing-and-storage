package ru.nsu.chebotareva.common;

import org.bouncycastle.openssl.jcajce.JcaPEMWriter;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

/**
 * Утилиты для конвертации криптографических объектов в формат PEM
 */
public class PemUtil {
    /**
     * Конвертирует приватный ключ в PEM формат
     * @param privateKey приватный ключ для конвертации
     * @return массив байтов с PEM представлением
     * @throws IOException при ошибках записи
     */
    public static byte[] privateKeyToPemBytes(PrivateKey privateKey) throws IOException {
        StringWriter stringWriter = new StringWriter();
        try (JcaPEMWriter pemWriter = new JcaPEMWriter(stringWriter)) {
            pemWriter.writeObject(privateKey);
        }
        return stringWriter.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Конвертирует сертификат в PEM формат
     * @param certificate сертификат для конвертации
     * @return массив байтов с PEM представлением
     * @throws IOException при ошибках записи
     */
    public static byte[] certificateToPemBytes(X509Certificate certificate) throws IOException {
        StringWriter stringWriter = new StringWriter();
        try (JcaPEMWriter pemWriter = new JcaPEMWriter(stringWriter)) {
            pemWriter.writeObject(certificate);
        }
        return stringWriter.toString().getBytes(StandardCharsets.UTF_8);
    }
}
