package ru.nsu.chebotareva;

import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

public class JaxbWriter {

    public void writeWithValidation(JaxbPeople people, Path outPath, Path schemaPath) throws JAXBException, SAXException, IOException {
        JAXBContext context = JAXBContext.newInstance(JaxbPeople.class);
        Marshaller marshaller = context.createMarshaller();

        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);

        SchemaFactory sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        Schema schema = sf.newSchema(schemaPath.toFile());
        marshaller.setSchema(schema);

        marshaller.setEventHandler(event -> {
            System.err.println("\nEVENT");
            System.err.println("SEVERITY:  " + event.getSeverity());
            System.err.println("MESSAGE:  " + event.getMessage());
            System.err.println("LINKED EXCEPTION:  " + event.getLinkedException());
            System.err.println("LOCATOR");
            System.err.println("    LINE NUMBER:  " + event.getLocator().getLineNumber());
            System.err.println("    COLUMN NUMBER:  " + event.getLocator().getColumnNumber());
            System.err.println("    OFFSET:  " + event.getLocator().getOffset());
            System.err.println("    OBJECT:  " + event.getLocator().getObject());
            System.err.println("    NODE:  " + event.getLocator().getNode());
            System.err.println("    URL:  " + event.getLocator().getURL());
            return false;
        });

        File outFile = outPath.toFile();
        if (!outFile.getParentFile().exists()) {
            outFile.getParentFile().mkdirs();
        }
        marshaller.marshal(people, outFile);
    }
}
