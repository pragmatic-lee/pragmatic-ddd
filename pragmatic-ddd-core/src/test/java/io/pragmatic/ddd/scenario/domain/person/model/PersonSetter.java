package io.pragmatic.ddd.scenario.domain.person.model;

import io.pragmatic.ddd.scenario.domain.person.model.valueobject.Address;
import io.pragmatic.ddd.scenario.domain.person.param.PersonInitData;
import io.pragmatic.ddd.scenario.domain.person.param.PersonUpdateData;

class PersonSetter {

    public static void init(Person person, PersonInitData personInitData){
        person.setAge(personInitData.getAge());
        person.setName(personInitData.getName());
        person.setEmail(personInitData.getEmail());
        person.setStatus(personInitData.getStatus());
        person.setPhone(personInitData.getPhone());
        person.setAddress(personInitData.getAddress() != null ? personInitData.getAddress() : new Address("", "", "", ""));
    }

    public static void updateSet(Person person, PersonUpdateData personUpdateData){
        person.setAge(personUpdateData.getAge());
        person.setName(personUpdateData.getName());
        person.setEmail(personUpdateData.getEmail());
        person.setPhone(personUpdateData.getPhone());
        if (personUpdateData.getAddress() != null) {
            person.setAddress(personUpdateData.getAddress());
        }
    }
}
