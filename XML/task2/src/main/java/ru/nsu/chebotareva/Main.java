package ru.nsu.chebotareva;

import org.xml.sax.SAXException;

import javax.xml.bind.JAXBException;
import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class Main {
    public static void main(String[] args) {
        Path input = Path.of("people.xml");

        try (InputStream in = Files.newInputStream(input)) {
            System.out.println("Reading " + input.toAbsolutePath());
            PeopleStaxParser parser = new PeopleStaxParser();
            PeopleRepository parsed = parser.parse(in);
            System.out.println("Parsed entries: " + parsed.allPersons().size());

            RelationshipRefiner refiner = new RelationshipRefiner();
            refiner.refineSiblingsByGender(parsed);

            PeopleMerger merger = new PeopleMerger();
            PeopleRepository merged = merger.merge(parsed);
            System.out.println("After merge (unique persons): " + merged.allPersons().size());

            refiner.refineSiblingsByGender(merged);

            ConsistencyChecker.Result res = new ConsistencyChecker().check(merged.allPersons());
            System.out.println("Checked persons: " + res.personsChecked);
            System.out.println("Children count mismatches: " + res.childrenMismatches);
            System.out.println("Siblings count mismatches: " + res.siblingsMismatches);

            JaxbMapper mapper = new JaxbMapper();
            JaxbPeople jaxbPeople = mapper.map(merged.allPersons());

            Path schemaPath = Path.of("src/main/resources/schema.xsd");
            Path outPath = Path.of("src/main/resources/people-jaxb.xml");

            JaxbWriter writer = new JaxbWriter();
            writer.writeWithValidation(jaxbPeople, outPath, schemaPath);
            System.out.println("JAXB XML written to: " + outPath.toAbsolutePath());

        } catch (IOException | XMLStreamException | JAXBException | SAXException e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
