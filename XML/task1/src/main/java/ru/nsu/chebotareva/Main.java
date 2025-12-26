package ru.nsu.chebotareva;

import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class Main {
    public static void main(String[] args) {
        Path input = Path.of("src/main/resources/people.xml");
        if (!Files.exists(input)) {
            System.err.println("Input XML not found: " + input.toAbsolutePath());
            System.err.println("Place people.xml into src/main/resources/people.xml and run again.");
            return;
        }

        try (InputStream in = Files.newInputStream(input)) {
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

            Path out = Path.of("src/main/resources/people-normalized.xml");
            Files.createDirectories(out.getParent());
            new PeopleXmlWriter().write(out, merged.allPersons());
            System.out.println("Normalized XML written to: " + out.toAbsolutePath());
        } catch (IOException e) {
            System.err.println("I/O error: " + e.getMessage());
        } catch (XMLStreamException e) {
            System.err.println("XML parse error: " + e.getMessage());
        }
    }
}
