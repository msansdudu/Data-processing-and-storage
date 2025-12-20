package ru.nsu.chebotareva.common;

public class KeyData {
    private final byte[] privateKeyPem;
    private final byte[] certificatePem;

    public KeyData(byte[] privateKeyPem, byte[] certificatePem) {
        if (privateKeyPem == null || certificatePem == null) {
            throw new IllegalArgumentException("privateKeyPem and certificatePem must not be null");
        }
        this.privateKeyPem = privateKeyPem.clone();
        this.certificatePem = certificatePem.clone();
    }

    public byte[] getPrivateKeyPem() {
        return privateKeyPem.clone();
    }

    public byte[] getCertificatePem() {
        return certificatePem.clone();
    }
}
