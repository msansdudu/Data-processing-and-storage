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

/**
 * Утилиты для криптографических операций с использованием BouncyCastle
 */
public class CryptoUtil {
    private static volatile boolean bouncyCastleInitialized;

    private static void initializeBouncyCastle() {
        if (!bouncyCastleInitialized) {
            synchronized (CryptoUtil.class) {
                if (!bouncyCastleInitialized) {
                    if (Security.getProvider("BC") == null) {
                        Security.addProvider(new BouncyCastleProvider());
                    }
                    bouncyCastleInitialized = true;
                }
            }
        }
    }

    /**
     * Загружает приватный ключ из PEM файла
     * @param pemFilePath путь к PEM файлу
     * @return приватный ключ
     * @throws IOException при ошибках чтения или парсинга
     */
    public static PrivateKey loadPrivateKeyFromPem(Path pemFilePath) throws IOException {
        initializeBouncyCastle();
        try (Reader reader = Files.newBufferedReader(pemFilePath, StandardCharsets.UTF_8);
             PEMParser pemParser = new PEMParser(reader)) {
            Object parsedObject = pemParser.readObject();
            if (parsedObject == null) {
                throw new IOException("PEM file is empty: " + pemFilePath);
            }
            JcaPEMKeyConverter keyConverter = new JcaPEMKeyConverter().setProvider("BC");
            if (parsedObject instanceof PrivateKeyInfo privateKeyInfo) {
                return keyConverter.getPrivateKey(privateKeyInfo);
            } else if (parsedObject instanceof PEMKeyPair pemKeyPair) {
                KeyPair keyPair = keyConverter.getKeyPair(pemKeyPair);
                return keyPair.getPrivate();
            } else if (parsedObject instanceof PemObject pemObject) {
                try (PEMParser retryParser = new PEMParser(
                        new BufferedReader(Files.newBufferedReader(pemFilePath, StandardCharsets.UTF_8)))) {
                    Object retryObject = retryParser.readObject();
                    if (retryObject instanceof PrivateKeyInfo privateKeyInfo2) {
                        return keyConverter.getPrivateKey(privateKeyInfo2);
                    }
                    if (retryObject instanceof PEMKeyPair pemKeyPair2) {
                        return keyConverter.getKeyPair(pemKeyPair2).getPrivate();
                    }
                }
                throw new IOException("Unsupported PEM object type: " + pemObject.getType());
            } else {
                throw new IOException("Unsupported PEM content type: " + parsedObject.getClass());
            }
        }
    }

    /**
     * Генерирует пару RSA ключей длиной 8192 бита
     * @return пара ключей (приватный и публичный)
     * @throws NoSuchAlgorithmException если алгоритм RSA недоступен
     */
    public static KeyPair generateRsa8192() throws NoSuchAlgorithmException {
        KeyPairGenerator rsaKeyGenerator = KeyPairGenerator.getInstance("RSA");
        rsaKeyGenerator.initialize(8192, new SecureRandom());
        return rsaKeyGenerator.generateKeyPair();
    }

    /**
     * Создает самоподписанный X.509 сертификат для публичного ключа
     * @param issuerDistinguishedName DN издателя сертификата
     * @param subjectCommonName CN субъекта сертификата
     * @param subjectPublicKey публичный ключ для сертификации
     * @param issuerPrivateKey приватный ключ для подписи сертификата
     * @return X.509 сертификат
     * @throws OperatorCreationException при ошибках создания подписи
     * @throws CertificateException при ошибках создания сертификата
     */
    public static X509Certificate issueCertificate(String issuerDistinguishedName, String subjectCommonName,
                                                  PublicKey subjectPublicKey, PrivateKey issuerPrivateKey)
            throws OperatorCreationException, CertificateException {
        initializeBouncyCastle();
        X500Principal certificateIssuer = new X500Principal(issuerDistinguishedName);
        X500Principal certificateSubject = new X500Principal("CN=" + subjectCommonName);
        Date validityStart = new Date(System.currentTimeMillis() - 5 * 60 * 1000); // 5 минут назад
        Date validityEnd = new Date(System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000); // 1 год вперед
        BigInteger certificateSerial = new BigInteger(160, new SecureRandom());
        JcaX509v3CertificateBuilder certificateBuilder = new JcaX509v3CertificateBuilder(
                certificateIssuer,
                certificateSerial,
                validityStart,
                validityEnd,
                certificateSubject,
                subjectPublicKey
        );
        ContentSigner contentSigner = new JcaContentSignerBuilder("SHA256withRSA")
                .setProvider("BC").build(issuerPrivateKey);
        X509CertificateHolder certificateHolder = certificateBuilder.build(contentSigner);
        return new JcaX509CertificateConverter().setProvider("BC").getCertificate(certificateHolder);
    }
}
