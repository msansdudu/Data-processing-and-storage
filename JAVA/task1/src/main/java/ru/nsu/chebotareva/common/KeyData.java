package ru.nsu.chebotareva.common;

/**
 * Контейнер для хранения сгенерированной пары ключей в формате PEM
 */
public class KeyData {
    private final byte[] privateKeyPemData;
    private final byte[] certificatePemData;

    /**
     * Создает контейнер с данными ключей
     * @param privateKeyPem PEM данные приватного ключа
     * @param certificatePem PEM данные сертификата
     */
    public KeyData(byte[] privateKeyPem, byte[] certificatePem) {
        if (privateKeyPem == null || certificatePem == null) {
            throw new IllegalArgumentException("PEM data for private key and certificate cannot be null");
        }
        this.privateKeyPemData = privateKeyPem.clone();
        this.certificatePemData = certificatePem.clone();
    }

    /**
     * Возвращает копию PEM данных приватного ключа
     * @return массив байтов с PEM данными
     */
    public byte[] getPrivateKeyPem() {
        return privateKeyPemData.clone();
    }

    /**
     * Возвращает копию PEM данных сертификата
     * @return массив байтов с PEM данными
     */
    public byte[] getCertificatePem() {
        return certificatePemData.clone();
    }
}
