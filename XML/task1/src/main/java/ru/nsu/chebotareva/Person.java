package ru.nsu.chebotareva;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class Person {
    private String id;
    private String firstName;
    private String lastName;
    private String gender;

    private String spouseId;
    private String spouseName;

    private final Set<String> fatherIds = new HashSet<>();
    private final Set<String> fatherNames = new HashSet<>();
    private final Set<String> motherIds = new HashSet<>();
    private final Set<String> motherNames = new HashSet<>();

    private final Set<String> sonIds = new HashSet<>();
    private final Set<String> daughterIds = new HashSet<>();

    private final Set<String> brotherIds = new HashSet<>();
    private final Set<String> sisterIds = new HashSet<>();

    private Integer declaredChildrenNumber;
    private Integer declaredSiblingsNumber;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getSpouseId() {
        return spouseId;
    }

    public void setSpouseId(String spouseId) {
        this.spouseId = spouseId;
    }

    public String getSpouseName() {
        return spouseName;
    }

    public void setSpouseName(String spouseName) {
        this.spouseName = spouseName;
    }

    public Set<String> getFatherIds() {
        return fatherIds;
    }

    public Set<String> getFatherNames() {
        return fatherNames;
    }

    public Set<String> getMotherIds() {
        return motherIds;
    }

    public Set<String> getMotherNames() {
        return motherNames;
    }

    public Set<String> getSonIds() {
        return sonIds;
    }

    public Set<String> getDaughterIds() {
        return daughterIds;
    }

    public Set<String> getBrotherIds() {
        return brotherIds;
    }

    public Set<String> getSisterIds() {
        return sisterIds;
    }

    public Integer getDeclaredChildrenNumber() {
        return declaredChildrenNumber;
    }

    public void setDeclaredChildrenNumber(Integer declaredChildrenNumber) {
        this.declaredChildrenNumber = declaredChildrenNumber;
    }

    public Integer getDeclaredSiblingsNumber() {
        return declaredSiblingsNumber;
    }

    public void setDeclaredSiblingsNumber(Integer declaredSiblingsNumber) {
        this.declaredSiblingsNumber = declaredSiblingsNumber;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Person person = (Person) o;
        return Objects.equals(id, person.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
