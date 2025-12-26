package ru.nsu.chebotareva;

import java.util.*;

public class PeopleMerger {
    public PeopleRepository merge(PeopleRepository repo) {
        Map<String, Person> byId = new HashMap<>();
        Map<String, Person> byNameKey = new HashMap<>();

        for (Person p : repo.allPersons()) {
            String id = emptyToNull(p.getId());
            if (id != null) {
                byId.merge(id, clonePersonWithId(id, p), PeopleMerger::mergePersons);
            } else {
                String key = nameKey(p);
                if (key == null) {
                    continue;
                }
                byNameKey.merge(key, clonePersonWithId(null, p), PeopleMerger::mergePersons);
            }
        }

        PeopleRepository out = new PeopleRepository();
        for (Map.Entry<String, Person> e : byId.entrySet()) {
            out.put(e.getValue());
        }
        for (Person p : byNameKey.values()) {
            if (p.getId() == null || p.getId().isEmpty()) {
                p.setId("NAMEKEY_" + nameKey(p));
            }
            if (out.findById(p.getId()).isEmpty()) {
                out.put(p);
            }
        }
        return out;
    }

    private static String emptyToNull(String s) {
        return (s == null || s.trim().isEmpty()) ? null : s;
    }

    private static String nameKey(Person p) {
        String f = p.getFirstName();
        String l = p.getLastName();
        if ((f == null || f.isBlank()) && (l == null || l.isBlank())) return null;
        String base = (normalizeSpaceLower(f) + "|" + normalizeSpaceLower(l));

        List<String> ctx = new ArrayList<>();
        if (!p.getFatherIds().isEmpty()) ctx.add("F:" + sortedJoin(p.getFatherIds()));
        else if (!p.getFatherNames().isEmpty()) ctx.add("FNAME:" + sortedJoin(p.getFatherNames()));

        if (!p.getMotherIds().isEmpty()) ctx.add("M:" + sortedJoin(p.getMotherIds()));
        else if (!p.getMotherNames().isEmpty()) ctx.add("MNAME:" + sortedJoin(p.getMotherNames()));

        if (p.getSpouseId() != null && !p.getSpouseId().isBlank()) ctx.add("S:" + p.getSpouseId().trim());
        else if (p.getSpouseName() != null && !p.getSpouseName().isBlank()) ctx.add("SNAME:" + normalizeSpaceLower(p.getSpouseName()));

        String ctxKey = String.join("|", ctx);
        if (!ctxKey.isEmpty()) return base + "|" + ctxKey;
        return base;
    }

    private static Person clonePersonWithId(String id, Person src) {
        Person p = new Person();
        p.setId(id);
        if (src.getFirstName() != null) p.setFirstName(src.getFirstName());
        if (src.getLastName() != null) p.setLastName(src.getLastName());
        if (src.getGender() != null) p.setGender(src.getGender());
        if (src.getSpouseId() != null) p.setSpouseId(src.getSpouseId());
        if (src.getSpouseName() != null) p.setSpouseName(src.getSpouseName());
        p.getFatherIds().addAll(src.getFatherIds());
        p.getFatherNames().addAll(src.getFatherNames());
        p.getMotherIds().addAll(src.getMotherIds());
        p.getMotherNames().addAll(src.getMotherNames());
        p.getSonIds().addAll(src.getSonIds());
        p.getDaughterIds().addAll(src.getDaughterIds());
        p.getBrotherIds().addAll(src.getBrotherIds());
        p.getSisterIds().addAll(src.getSisterIds());
        if (src.getDeclaredChildrenNumber() != null) p.setDeclaredChildrenNumber(src.getDeclaredChildrenNumber());
        if (src.getDeclaredSiblingsNumber() != null) p.setDeclaredSiblingsNumber(src.getDeclaredSiblingsNumber());
        return p;
    }

    private static Person mergePersons(Person a, Person b) {
        if (isBlank(a.getFirstName()) && !isBlank(b.getFirstName())) a.setFirstName(b.getFirstName());
        if (isBlank(a.getLastName()) && !isBlank(b.getLastName())) a.setLastName(b.getLastName());
        if (isBlank(a.getGender()) && !isBlank(b.getGender())) a.setGender(b.getGender());
        if (isBlank(a.getSpouseId()) && !isBlank(b.getSpouseId())) a.setSpouseId(b.getSpouseId());
        if (isBlank(a.getSpouseName()) && !isBlank(b.getSpouseName())) a.setSpouseName(b.getSpouseName());
        if (a.getDeclaredChildrenNumber() == null) a.setDeclaredChildrenNumber(b.getDeclaredChildrenNumber());
        if (a.getDeclaredSiblingsNumber() == null) a.setDeclaredSiblingsNumber(b.getDeclaredSiblingsNumber());
        a.getFatherIds().addAll(b.getFatherIds());
        a.getFatherNames().addAll(b.getFatherNames());
        a.getMotherIds().addAll(b.getMotherIds());
        a.getMotherNames().addAll(b.getMotherNames());
        a.getSonIds().addAll(b.getSonIds());
        a.getDaughterIds().addAll(b.getDaughterIds());
        a.getBrotherIds().addAll(b.getBrotherIds());
        a.getSisterIds().addAll(b.getSisterIds());
        return a;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String normalizeSpaceLower(String s) {
        if (s == null) return "";
        return s.trim().replaceAll("\\s+", " ").toLowerCase();
    }

    private static String sortedJoin(Collection<String> values) {
        List<String> list = new ArrayList<>();
        for (String v : values) {
            if (v != null && !v.isBlank()) list.add(v.trim());
        }
        Collections.sort(list);
        return String.join(",", list);
    }
}
