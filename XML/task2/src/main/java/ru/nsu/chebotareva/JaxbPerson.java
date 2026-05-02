package ru.nsu.chebotareva;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlID;
import javax.xml.bind.annotation.XmlIDREF;
import javax.xml.bind.annotation.XmlType;
import java.util.ArrayList;
import java.util.List;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "PersonType", propOrder = {
        "firstName", "lastName", "gender", "spouse", "fathers", "mothers", "sons", "daughters", "brothers", "sisters"
})
public class JaxbPerson {

    @XmlAttribute(name = "id", required = true)
    @XmlID
    private String id;

    @XmlElement(name = "firstName")
    private String firstName;

    @XmlElement(name = "lastName")
    private String lastName;

    @XmlElement(name = "gender")
    private String gender;

    @XmlIDREF
    @XmlElement(name = "spouse")
    private JaxbPerson spouse;

    @XmlIDREF
    @XmlElement(name = "father")
    private List<JaxbPerson> fathers = new ArrayList<>();

    @XmlIDREF
    @XmlElement(name = "mother")
    private List<JaxbPerson> mothers = new ArrayList<>();

    @XmlIDREF
    @XmlElement(name = "son")
    private List<JaxbPerson> sons = new ArrayList<>();

    @XmlIDREF
    @XmlElement(name = "daughter")
    private List<JaxbPerson> daughters = new ArrayList<>();

    @XmlIDREF
    @XmlElement(name = "brother")
    private List<JaxbPerson> brothers = new ArrayList<>();

    @XmlIDREF
    @XmlElement(name = "sister")
    private List<JaxbPerson> sisters = new ArrayList<>();

    // Getters and Setters

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

    public JaxbPerson getSpouse() {
        return spouse;
    }

    public void setSpouse(JaxbPerson spouse) {
        this.spouse = spouse;
    }

    public List<JaxbPerson> getFathers() {
        return fathers;
    }

    public List<JaxbPerson> getMothers() {
        return mothers;
    }

    public List<JaxbPerson> getSons() {
        return sons;
    }

    public List<JaxbPerson> getDaughters() {
        return daughters;
    }

    public List<JaxbPerson> getBrothers() {
        return brothers;
    }

    public List<JaxbPerson> getSisters() {
        return sisters;
    }
}
