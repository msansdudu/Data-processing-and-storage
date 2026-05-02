package ru.nsu.chebotareva;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class PeopleRepository {
    private final Map<String, Person> personsById = new HashMap<>();

    public Person getOrCreateById(String id) {
        return personsById.computeIfAbsent(id, k -> {
            Person p = new Person();
            p.setId(k);
            return p;
        });
    }

    public Optional<Person> findById(String id) {
        return Optional.ofNullable(personsById.get(id));
    }

    public void put(Person person) {
        if (person.getId() == null || person.getId().isEmpty()) {
            throw new IllegalArgumentException("Person id must not be null or empty");
        }
        personsById.put(person.getId(), person);
    }

    public Collection<Person> allPersons() {
        return personsById.values();
    }
}
