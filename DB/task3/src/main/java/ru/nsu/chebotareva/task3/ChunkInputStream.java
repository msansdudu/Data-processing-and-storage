package ru.nsu.chebotareva.task3;

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;

public class ChunkInputStream extends InputStream {
    private final RandomAccessFile file;
    private final long endPos;
    private boolean readingPrefix = true;
    private boolean readingSuffix = false;
    private int prefixIndex = 0;
    private int suffixIndex = 0;
    
    private final byte[] prefix = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<people>\n".getBytes(StandardCharsets.UTF_8);
    private final byte[] suffix = "\n</people>".getBytes(StandardCharsets.UTF_8);
    
    private final byte[] personTag = "<person".getBytes(StandardCharsets.UTF_8);
    private final byte[] endPersonTag = "</person>".getBytes(StandardCharsets.UTF_8);
    
    private int personTagMatch = 0;
    private int endPersonTagMatch = 0;
    private boolean pastEnd = false;
    private boolean reachedEOF = false;
    private final boolean appendSuffix;
    
    public ChunkInputStream(String filePath, long startPos, long endPos, boolean appendSuffix) throws IOException {
        this.file = new RandomAccessFile(filePath, "r");
        this.endPos = endPos;
        this.appendSuffix = appendSuffix;
        
        if (startPos > 0) {
            file.seek(startPos);
            while (true) {
                int b = file.read();
                if (b == -1) {
                    reachedEOF = true;
                    break;
                }
                if (b == personTag[personTagMatch]) {
                    personTagMatch++;
                    if (personTagMatch == personTag.length) {
                        file.seek(file.getFilePointer() - personTag.length);
                        break;
                    }
                } else {
                    if (b == personTag[0]) {
                        personTagMatch = 1;
                    } else {
                        personTagMatch = 0;
                    }
                }
            }
        } else {
            while (true) {
                int b = file.read();
                if (b == -1) {
                    reachedEOF = true;
                    break;
                }
                if (b == personTag[personTagMatch]) {
                    personTagMatch++;
                    if (personTagMatch == personTag.length) {
                        file.seek(file.getFilePointer() - personTag.length);
                        break;
                    }
                } else {
                    if (b == personTag[0]) {
                        personTagMatch = 1;
                    } else {
                        personTagMatch = 0;
                    }
                }
            }
        }
    }

    @Override
    public int read() throws IOException {
        if (readingPrefix) {
            if (prefixIndex < prefix.length) {
                return prefix[prefixIndex++];
            } else {
                readingPrefix = false;
            }
        }
        
        if (reachedEOF) {
            return readSuffix();
        }
        
        int b = file.read();
        if (b == -1) {
            reachedEOF = true;
            return readSuffix();
        }
        
        long currentPos = file.getFilePointer();
        if (currentPos >= endPos) {
            pastEnd = true;
        }
        
        if (pastEnd) {
            if (b == endPersonTag[endPersonTagMatch]) {
                endPersonTagMatch++;
                if (endPersonTagMatch == endPersonTag.length) {
                    reachedEOF = true;
                }
            } else {
                if (b == endPersonTag[0]) {
                    endPersonTagMatch = 1;
                } else {
                    endPersonTagMatch = 0;
                }
            }
        }
        
        return b;
    }
    
    private int readSuffix() {
        if (!appendSuffix) {
            return -1;
        }
        if (!readingSuffix) {
            readingSuffix = true;
            suffixIndex = 0;
        }
        if (suffixIndex < suffix.length) {
            return suffix[suffixIndex++];
        }
        return -1;
    }

    @Override
    public void close() throws IOException {
        file.close();
    }
}
