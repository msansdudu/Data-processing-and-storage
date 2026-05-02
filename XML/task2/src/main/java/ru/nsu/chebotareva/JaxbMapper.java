package ru.nsu.chebotareva;

import java.util.HashMap;
import java.util.Map;

public class JaxbMapper {

    public JaxbPeople map(Iterable<Person> originalPeople) {
        JaxbPeople people = new JaxbPeople();
        Map<String, JaxbPerson> map = new HashMap<>();

        for (Person p : originalPeople) {
            if (p.getId() != null) {
                JaxbPerson jp = new JaxbPerson();
                jp.setId(p.getId());
                jp.setFirstName(p.getFirstName());
                jp.setLastName(p.getLastName());
                jp.setGender(p.getGender());
                map.put(p.getId(), jp);
                people.getPersons().add(jp);
            }
        }

        for (Person p : originalPeople) {
            if (p.getId() == null) continue;
            JaxbPerson jp = map.get(p.getId());

            if (p.getSpouseId() != null) {
                jp.setSpouse(map.get(p.getSpouseId()));
            }

            for (String fatherId : p.getFatherIds()) {
                if (map.containsKey(fatherId)) jp.getFathers().add(map.get(fatherId));
            }

            for (String motherId : p.getMotherIds()) {
                if (map.containsKey(motherId)) jp.getMothers().add(map.get(motherId));
            }

            for (String sonId : p.getSonIds()) {
                if (map.containsKey(sonId)) jp.getSons().add(map.get(sonId));
            }

            for (String daughterId : p.getDaughterIds()) {
                if (map.containsKey(daughterId)) jp.getDaughters().add(map.get(daughterId));
            }

            for (String brotherId : p.getBrotherIds()) {
                if (map.containsKey(brotherId)) jp.getBrothers().add(map.get(brotherId));
            }

            for (String sisterId : p.getSisterIds()) {
                if (map.containsKey(sisterId)) jp.getSisters().add(map.get(sisterId));
            }
        }

        return people;
    }
}
