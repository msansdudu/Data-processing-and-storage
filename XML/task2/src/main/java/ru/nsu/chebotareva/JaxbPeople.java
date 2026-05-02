package ru.nsu.chebotareva;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

@XmlRootElement(name = "people")
@XmlAccessorType(XmlAccessType.FIELD)
public class JaxbPeople {

    @XmlElement(name = "person")
    private List<JaxbPerson> persons = new ArrayList<>();

    public List<JaxbPerson> getPersons() {
        return persons;
    }

    public void setPersons(List<JaxbPerson> persons) {
        this.persons = persons;
    }
}
