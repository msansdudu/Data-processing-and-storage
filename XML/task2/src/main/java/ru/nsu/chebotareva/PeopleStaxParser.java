package ru.nsu.chebotareva;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.InputStream;

public class PeopleStaxParser {
    public PeopleRepository parse(InputStream in) throws XMLStreamException {
        PeopleRepository repo = new PeopleRepository();
        XMLInputFactory factory = XMLInputFactory.newInstance();
        XMLStreamReader reader = factory.createXMLStreamReader(in);

        Person current = null;
        String currentElement = null;

        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                String name = reader.getLocalName();
                currentElement = name;
                if ("person".equals(name)) {
                    current = new Person();
                    String idAttr = getAttr(reader, "id");
                    if (idAttr != null && !idAttr.isEmpty()) {
                        current.setId(idAttr.trim());
                    }
                } else if ("id".equals(name)) {
                    String v = getAttr(reader, "value");
                    if (current != null && v != null && !v.isEmpty()) {
                        current.setId(v.trim());
                    }
                } else if ("firstname".equals(name) || "first".equals(name)) {
                    String v = getAttr(reader, "value");
                    if (current != null && v != null && !v.isEmpty()) {
                        if (current.getFirstName() == null || current.getFirstName().isEmpty()) {
                            current.setFirstName(v.trim());
                        }
                    }
                } else if ("surname".equals(name) || "family".equals(name)) {
                    String v = getAttr(reader, "value");
                    if (current != null && v != null && !v.isEmpty()) {
                        if (current.getLastName() == null || current.getLastName().isEmpty()) {
                            current.setLastName(v.trim());
                        }
                    }
                } else if ("gender".equals(name)) {
                    String v = getAttr(reader, "value");
                    if (current != null && v != null) {
                        String g = normalizeGender(v);
                        if (g != null) current.setGender(g);
                    }
                } else if ("wife".equals(name) || "husband".equals(name) || "spouce".equals(name)) {
                    if (current != null) {
                        String v = getAttr(reader, "value");
                        if (v != null && !v.isEmpty()) {
                            if (looksLikeId(v)) current.setSpouseId(v.trim()); else current.setSpouseName(v.trim());
                        }
                    }
                } else if ("children-number".equals(name)) {
                    if (current != null) {
                        Integer n = parseIntSafe(getAttr(reader, "value"));
                        if (n != null) current.setDeclaredChildrenNumber(n);
                    }
                } else if ("siblings-number".equals(name)) {
                    if (current != null) {
                        Integer n = parseIntSafe(getAttr(reader, "value"));
                        if (n != null) current.setDeclaredSiblingsNumber(n);
                    }
                } else if ("siblings".equals(name)) {
                    if (current != null) {
                        String val = getAttr(reader, "val");
                        if (val != null && !val.isEmpty()) {
                            for (String token : val.trim().split("\\s+")) {
                                if (token.isEmpty()) continue;
                                if (looksLikeId(token)) {
                                    current.getBrotherIds().add(token);
                                }
                            }
                        }
                    }
                } else if ("son".equals(name)) {
                    if (current != null) {
                        String id = getAttr(reader, "id");
                        if (id != null && !id.isEmpty()) current.getSonIds().add(id.trim());
                    }
                } else if ("daughter".equals(name)) {
                    if (current != null) {
                        String id = getAttr(reader, "id");
                        if (id != null && !id.isEmpty()) current.getDaughterIds().add(id.trim());
                    }
                } else if ("father".equals(name)) {
                    if (current != null) {
                        String v = getAttr(reader, "value");
                        if (v != null && !v.isEmpty() && !"UNKNOWN".equalsIgnoreCase(v)) {
                            if (looksLikeId(v)) current.getFatherIds().add(v.trim()); else current.getFatherNames().add(v.trim());
                        }
                    }
                } else if ("mother".equals(name)) {
                    if (current != null) {
                        String v = getAttr(reader, "value");
                        if (v != null && !v.isEmpty() && !"UNKNOWN".equalsIgnoreCase(v)) {
                            if (looksLikeId(v)) current.getMotherIds().add(v.trim()); else current.getMotherNames().add(v.trim());
                        }
                    }
                } else if ("parent".equals(name)) {
                    if (current != null) {
                        String v = getAttr(reader, "value");
                        if (v != null && !v.isEmpty() && !"UNKNOWN".equalsIgnoreCase(v)) {
                            if (looksLikeId(v)) {
                                current.getFatherIds().add(v.trim());
                                current.getMotherIds().add(v.trim());
                            } else {
                                current.getFatherNames().add(v.trim());
                                current.getMotherNames().add(v.trim());
                            }
                        }
                    }
                }
            } else if (event == XMLStreamConstants.CHARACTERS) {
                String text = reader.getText();
                if (current != null && currentElement != null) {
                    if ("firstname".equals(currentElement) || "first".equals(currentElement)) {
                        String v = text.trim();
                        if (!v.isEmpty() && (current.getFirstName() == null || current.getFirstName().isEmpty())) {
                            current.setFirstName(v);
                        }
                    } else if ("surname".equals(currentElement) || "family".equals(currentElement)) {
                        String v = text.trim();
                        if (!v.isEmpty() && (current.getLastName() == null || current.getLastName().isEmpty())) {
                            current.setLastName(v);
                        }
                    } else if ("gender".equals(currentElement)) {
                        String g = normalizeGender(text);
                        if (g != null) current.setGender(g);
                    } else if ("father".equals(currentElement)) {
                        String val = text.trim();
                        if (!val.isEmpty() && !"UNKNOWN".equalsIgnoreCase(val)) current.getFatherNames().add(val);
                    } else if ("mother".equals(currentElement)) {
                        String val = text.trim();
                        if (!val.isEmpty() && !"UNKNOWN".equalsIgnoreCase(val)) current.getMotherNames().add(val);
                    } else if ("parent".equals(currentElement)) {
                        String val = text.trim();
                        if (!val.isEmpty() && !"UNKNOWN".equalsIgnoreCase(val)) {
                            current.getFatherNames().add(val);
                            current.getMotherNames().add(val);
                        }
                    } else if ("brother".equals(currentElement)) {
                        String val = text.trim();
                        if (!val.isEmpty()) {
                            String sid = synthesizeIdFromName(val);
                            current.getBrotherIds().add(sid);
                            // Ensure the referenced sibling person exists in the repository
                            Person sib = repo.getOrCreateById(sid);
                            fillNamesIfEmpty(sib, val);
                        }
                    } else if ("sister".equals(currentElement)) {
                        String val = text.trim();
                        if (!val.isEmpty()) {
                            String sid = synthesizeIdFromName(val);
                            current.getSisterIds().add(sid);
                            // Ensure the referenced sibling person exists in the repository
                            Person sib = repo.getOrCreateById(sid);
                            fillNamesIfEmpty(sib, val);
                        }
                    }
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                String name = reader.getLocalName();
                if ("person".equals(name) && current != null) {
                    if (current.getId() == null || current.getId().isEmpty()) {
                        String synthesized = synthesizeId(current);
                        if (synthesized != null) {
                            current.setId(synthesized);
                        }
                    }
                    if (current.getId() != null) {
                        repo.put(current);
                    }
                    current = null;
                }
                currentElement = null;
            }
        }

        reader.close();
        return repo;
    }

    private static String normalizeGender(String raw) {
        if (raw == null) return null;
        String r = raw.trim().toLowerCase();
        if (r.isEmpty()) return null;
        if ("m".equals(r) || "male".equals(r)) return "male";
        if ("f".equals(r) || "female".equals(r)) return "female";
        return null;
    }

    private static String getAttr(XMLStreamReader reader, String name) {
        String v = reader.getAttributeValue(null, name);
        if (v != null) return v;
        int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            if (name.equals(reader.getAttributeLocalName(i))) {
                return reader.getAttributeValue(i);
            }
        }
        return null;
    }

    private static String synthesizeId(Person p) {
        if (p.getFirstName() != null || p.getLastName() != null) {
            String f = p.getFirstName() == null ? "" : p.getFirstName();
            String l = p.getLastName() == null ? "" : p.getLastName();
            String base = (f + "_" + l).trim();
            if (!base.equals("_")) {
                return ("NAME_" + base).replaceAll("\\s+", "_");
            }
        }
        return null;
    }

    private static String synthesizeIdFromName(String name) {
        if (name == null) return null;
        String base = name.trim();
        if (base.isEmpty()) return null;
        return ("NAME_" + base).replaceAll("\\s+", "_");
    }

    private static void fillNamesIfEmpty(Person p, String fullName) {
        if (p == null || fullName == null) return;
        String trimmed = fullName.trim().replaceAll("\\s+", " ");
        if (trimmed.isEmpty()) return;
        String first = null;
        String last = null;
        String[] parts = trimmed.split(" ");
        if (parts.length == 1) {
            // Only one token — treat it as last name if last is empty, otherwise as first name
            if (isBlank(p.getLastName())) last = parts[0]; else first = parts[0];
        } else {
            first = parts[0];
            last = parts[parts.length - 1];
        }
        if (isBlank(p.getFirstName()) && first != null && !first.isBlank()) p.setFirstName(first);
        if (isBlank(p.getLastName()) && last != null && !last.isBlank()) p.setLastName(last);
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static boolean looksLikeId(String s) {
        if (s == null) return false;
        return s.matches("P\\d+") || s.startsWith("NAME_");
    }

    private static Integer parseIntSafe(String s) {
        if (s == null) return null;
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
