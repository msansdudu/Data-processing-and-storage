package ru.nsu.chebotareva;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RelationshipRefiner {
    public void refineSiblingsByGender(PeopleRepository repo) {
        for (Person p : repo.allPersons()) {
            Set<String> allSiblingIds = new HashSet<>();
            allSiblingIds.addAll(p.getBrotherIds());
            allSiblingIds.addAll(p.getSisterIds());

            List<String> toSisters = new ArrayList<>();
            List<String> toBrothers = new ArrayList<>();

            for (String sibId : allSiblingIds) {
                Person sib = repo.findById(sibId).orElse(null);
                if (sib == null) continue;
                String g = sib.getGender();
                if (g == null) continue;
                if ("female".equalsIgnoreCase(g)) {
                    toSisters.add(sibId);
                } else if ("male".equalsIgnoreCase(g)) {
                    toBrothers.add(sibId);
                }
            }

            for (String id : toSisters) {
                p.getBrotherIds().remove(id);
                p.getSisterIds().add(id);
            }
            for (String id : toBrothers) {
                p.getSisterIds().remove(id);
                p.getBrotherIds().add(id);
            }
        }
    }
}
