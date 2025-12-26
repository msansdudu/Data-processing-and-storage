package ru.nsu.chebotareva;

import java.util.Collection;

public class ConsistencyChecker {
    public static class Result {
        public int personsChecked;
        public int childrenMismatches;
        public int siblingsMismatches;
    }

    public Result check(Collection<Person> persons) {
        Result r = new Result();
        for (Person p : persons) {
            r.personsChecked++;
            if (p.getDeclaredChildrenNumber() != null) {
                int actualChildren = p.getSonIds().size() + p.getDaughterIds().size();
                if (actualChildren != p.getDeclaredChildrenNumber()) {
                    r.childrenMismatches++;
                }
            }
            if (p.getDeclaredSiblingsNumber() != null) {
                int actualSiblings = p.getBrotherIds().size() + p.getSisterIds().size();
                if (actualSiblings != p.getDeclaredSiblingsNumber()) {
                    r.siblingsMismatches++;
                }
            }
        }
        return r;
    }
}
