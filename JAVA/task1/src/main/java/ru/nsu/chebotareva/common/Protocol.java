package ru.nsu.chebotareva.common;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public final class Protocol {
    private Protocol() {}

    public static final int MAX_NAME_LEN = 1024;
    public static final Charset NAME_CHARSET = StandardCharsets.US_ASCII;
    public static final byte NAME_TERMINATOR = 0x00;

    public static final int LENGTH_FIELD_BYTES = 4;

    public static final int ERROR_LENGTH = 0;
}
