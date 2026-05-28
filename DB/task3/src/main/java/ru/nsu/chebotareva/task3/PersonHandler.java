package ru.nsu.chebotareva.task3;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class PersonHandler extends DefaultHandler {
    private final Connection connection;
    private final PreparedStatement insertPersonStmt;
    private final PreparedStatement insertRelStmt;
    
    private String currentPersonId = null;
    private String firstName = null;
    private String lastName = null;
    private String gender = null;
    
    private StringBuilder textBuffer = new StringBuilder();
    private String currentElement = "";
    
    private List<String[]> relationships = new ArrayList<>();
    
    private int batchSize = 0;
    private static final int BATCH_LIMIT = 500;

    public PersonHandler(Connection connection) throws SQLException {
        this.connection = connection;
        this.connection.setAutoCommit(false);
        this.insertPersonStmt = connection.prepareStatement(
            "INSERT INTO raw_person (id, first_name, last_name, gender) VALUES (?, ?, ?, ?)"
        );
        this.insertRelStmt = connection.prepareStatement(
            "INSERT INTO raw_relationship (person_id, rel_type, rel_target) VALUES (?, ?, ?)"
        );
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
        currentElement = qName;
        textBuffer.setLength(0);
        
        if (qName.equals("person")) {
            currentPersonId = attributes.getValue("id");
            if (currentPersonId == null) {
                currentPersonId = "UNKNOWN_" + UUID.randomUUID().toString();
            }
            firstName = null;
            lastName = null;
            gender = null;
            relationships.clear();
            
            if (attributes.getValue("name") != null) {
                String[] parts = attributes.getValue("name").split(" ");
                if (parts.length > 0) firstName = parts[0];
                if (parts.length > 1) lastName = parts[parts.length - 1];
            }
        } else {
            String val = attributes.getValue("val");
            if (val == null) val = attributes.getValue("value");
            if (val == null) val = attributes.getValue("id");
            
            if (val != null) {
                if (qName.equals("gender")) {
                    gender = val.trim();
                } else if (qName.equalsIgnoreCase("firstname") || qName.equalsIgnoreCase("first")) {
                    firstName = val.trim();
                } else if (qName.equalsIgnoreCase("surname") || qName.equalsIgnoreCase("family") || qName.equalsIgnoreCase("lastname")) {
                    lastName = val.trim();
                } else if (isRelationshipTag(qName)) {
                    relationships.add(new String[]{currentPersonId, qName, val.trim()});
                }
            }
        }
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        textBuffer.append(ch, start, length);
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        String text = textBuffer.toString().trim();
        
        if (qName.equals("person")) {
            try {
                insertPersonStmt.setString(1, currentPersonId);
                insertPersonStmt.setString(2, firstName);
                insertPersonStmt.setString(3, lastName);
                insertPersonStmt.setString(4, gender);
                insertPersonStmt.addBatch();
                
                for (String[] rel : relationships) {
                    insertRelStmt.setString(1, rel[0]);
                    insertRelStmt.setString(2, rel[1]);
                    insertRelStmt.setString(3, rel[2]);
                    insertRelStmt.addBatch();
                }
                
                batchSize++;
                if (batchSize >= BATCH_LIMIT) {
                    flush();
                }
            } catch (SQLException e) {
                throw new SAXException(e);
            }
        } else if (!text.isEmpty()) {
            if (qName.equals("gender")) {
                gender = text;
            } else if (qName.equalsIgnoreCase("firstname") || qName.equalsIgnoreCase("first")) {
                firstName = text;
            } else if (qName.equalsIgnoreCase("surname") || qName.equalsIgnoreCase("family") || qName.equalsIgnoreCase("lastname")) {
                lastName = text;
            } else if (isRelationshipTag(qName)) {
                relationships.add(new String[]{currentPersonId, qName, text});
            }
        }
    }
    
    private boolean isRelationshipTag(String qName) {
        return qName.equals("wife") || qName.equals("husband") || qName.equals("spouce") || qName.equals("spouse") ||
               qName.equals("mother") || qName.equals("father") || qName.equals("parent") ||
               qName.equals("siblings") || qName.equals("brother") || qName.equals("sister") ||
               qName.equals("son") || qName.equals("daughter") || qName.equals("child") || qName.equals("children");
    }

    public void flush() throws SQLException {
        if (batchSize > 0) {
            insertPersonStmt.executeBatch();
            insertRelStmt.executeBatch();
            connection.commit();
            batchSize = 0;
        }
    }
}
