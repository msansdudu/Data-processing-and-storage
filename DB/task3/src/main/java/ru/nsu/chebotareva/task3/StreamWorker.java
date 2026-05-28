package ru.nsu.chebotareva.task3;

import java.sql.Connection;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.InputSource;

public class StreamWorker implements Runnable {
    private final String filePath;
    private final long startPos;
    private final long endPos;
    private final DatabaseManager dbManager;
    private final int threadId;
    private final boolean appendSuffix;

    public StreamWorker(int threadId, String filePath, long startPos, long endPos, DatabaseManager dbManager, boolean appendSuffix) {
        this.threadId = threadId;
        this.filePath = filePath;
        this.startPos = startPos;
        this.endPos = endPos;
        this.dbManager = dbManager;
        this.appendSuffix = appendSuffix;
    }

    @Override
    public void run() {
        System.out.println("Thread " + threadId + " started. Range: [" + startPos + ", " + endPos + ")");
        try (Connection conn = dbManager.getConnection();
             ChunkInputStream cis = new ChunkInputStream(filePath, startPos, endPos, appendSuffix)) {
            
            SAXParserFactory factory = SAXParserFactory.newInstance();
            SAXParser saxParser = factory.newSAXParser();
            
            PersonHandler handler = new PersonHandler(conn);
            InputSource is = new InputSource(cis);
            is.setEncoding("UTF-8");
            
            saxParser.parse(is, handler);
            handler.flush();
            System.out.println("Thread " + threadId + " finished processing.");
            
        } catch (Exception e) {
            System.err.println("Thread " + threadId + " encountered an error:");
            e.printStackTrace();
        }
    }
}
