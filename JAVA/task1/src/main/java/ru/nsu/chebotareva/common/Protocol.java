package ru.nsu.chebotareva.common;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Константы протокола взаимодействия между клиентом и сервером генерации ключей
 */
public final class Protocol {
    private Protocol() {}

    /** Максимальная длина имени клиента в байтах */
    public static final int MAX_NAME_LEN = 1024;

    /** Кодировка для передачи имен клиентов */
    public static final Charset NAME_CHARSET = StandardCharsets.US_ASCII;

    /** Терминатор строки имени (null byte) */
    public static final byte NAME_TERMINATOR = 0x00;

    /** Размер поля длины в байтах (big-endian int) */
    public static final int LENGTH_FIELD_BYTES = 4;

    /** Значение длины, указывающее на ошибку */
    public static final int ERROR_LENGTH = 0;
}
