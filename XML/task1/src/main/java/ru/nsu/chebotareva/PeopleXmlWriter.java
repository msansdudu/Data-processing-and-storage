package ru.nsu.chebotareva;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class PeopleXmlWriter {
    public void write(Path outputFile, Iterable<Person> people) throws IOException, XMLStreamException {
        try (OutputStream os = Files.newOutputStream(outputFile)) {
            XMLOutputFactory f = XMLOutputFactory.newInstance();
            XMLStreamWriter w = f.createXMLStreamWriter(os, "UTF-8");
            w.writeStartDocument("UTF-8", "1.0");
            w.writeCharacters("\n");
            w.writeStartElement("people");

            for (Person p : people) {
                indent(w, 1);
                w.writeStartElement("person");
                if (p.getId() != null) w.writeAttribute("id", p.getId());

                if (p.getFirstName() != null) {
                    indent(w, 2);
                    w.writeStartElement("first-name");
                    w.writeCharacters(p.getFirstName());
                    w.writeEndElement();
                }
                if (p.getLastName() != null) {
                    indent(w, 2);
                    w.writeStartElement("last-name");
                    w.writeCharacters(p.getLastName());
                    w.writeEndElement();
                }
                if (p.getGender() != null) {
                    indent(w, 2);
                    w.writeStartElement("gender");
                    w.writeCharacters(p.getGender());
                    w.writeEndElement();
                }

                if (p.getSpouseId() != null || p.getSpouseName() != null) {
                    indent(w, 2);
                    w.writeStartElement("spouse");
                    if (p.getSpouseId() != null) w.writeAttribute("ref", p.getSpouseId());
                    if (p.getSpouseName() != null) w.writeAttribute("name", p.getSpouseName());
                    w.writeEndElement();
                }

                if (!p.getFatherIds().isEmpty() || !p.getFatherNames().isEmpty() || !p.getMotherIds().isEmpty() || !p.getMotherNames().isEmpty()) {
                    indent(w, 2);
                    w.writeStartElement("parents");
                    for (String id : p.getFatherIds()) {
                        indent(w, 3);
                        w.writeEmptyElement("father");
                        w.writeAttribute("ref", id);
                    }
                    for (String name : p.getFatherNames()) {
                        indent(w, 3);
                        w.writeEmptyElement("father");
                        w.writeAttribute("name", name);
                    }
                    for (String id : p.getMotherIds()) {
                        indent(w, 3);
                        w.writeEmptyElement("mother");
                        w.writeAttribute("ref", id);
                    }
                    for (String name : p.getMotherNames()) {
                        indent(w, 3);
                        w.writeEmptyElement("mother");
                        w.writeAttribute("name", name);
                    }
                    indent(w, 2);
                    w.writeEndElement();
                }

                if (!p.getSonIds().isEmpty() || !p.getDaughterIds().isEmpty()) {
                    indent(w, 2);
                    w.writeStartElement("children");
                    for (String id : p.getSonIds()) {
                        indent(w, 3);
                        w.writeEmptyElement("son");
                        w.writeAttribute("ref", id);
                    }
                    for (String id : p.getDaughterIds()) {
                        indent(w, 3);
                        w.writeEmptyElement("daughter");
                        w.writeAttribute("ref", id);
                    }
                    indent(w, 2);
                    w.writeEndElement();
                }

                if (!p.getBrotherIds().isEmpty() || !p.getSisterIds().isEmpty()) {
                    indent(w, 2);
                    w.writeStartElement("siblings");
                    for (String id : p.getBrotherIds()) {
                        indent(w, 3);
                        w.writeEmptyElement("brother");
                        w.writeAttribute("ref", id);
                    }
                    for (String id : p.getSisterIds()) {
                        indent(w, 3);
                        w.writeEmptyElement("sister");
                        w.writeAttribute("ref", id);
                    }
                    indent(w, 2);
                    w.writeEndElement();
                }

                indent(w, 1);
                w.writeEndElement();
            }

            w.writeCharacters("\n");
            w.writeEndElement();
            w.writeCharacters("\n");
            w.writeEndDocument();
            w.flush();
            w.close();
        }
    }

    private static void indent(XMLStreamWriter w, int level) throws XMLStreamException {
        w.writeCharacters("\n");
        for (int i = 0; i < level; i++) {
            w.writeCharacters("  ");
        }
    }
}
