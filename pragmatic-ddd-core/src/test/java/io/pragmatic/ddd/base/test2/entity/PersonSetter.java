package io.pragmatic.ddd.base.test2.entity;

import io.pragmatic.ddd.base.test2.boxvalueobject.PersonInitData;
import io.pragmatic.ddd.base.test2.boxvalueobject.PersonUpdateData;

import java.util.Date;

class PersonSetter {

    public static void init(Person person, PersonInitData personInitData){
        person.setAge(personInitData.getAge());
        person.setName(personInitData.getName());
        person.setEmail(personInitData.getEmail());
        person.setStatus(personInitData.getStatus());
        person.setPhone(personInitData.getPhone());
        person.setCreatedTime(new Date());
    }

    public static void updateSet(Person person, PersonUpdateData personUpdateData){
        person.setAge(personUpdateData.getAge());
        person.setName(personUpdateData.getName());
        person.setEmail(personUpdateData.getEmail());
        person.setPhone(personUpdateData.getPhone());
        person.setUpdatedTime(new Date());
    }
}
