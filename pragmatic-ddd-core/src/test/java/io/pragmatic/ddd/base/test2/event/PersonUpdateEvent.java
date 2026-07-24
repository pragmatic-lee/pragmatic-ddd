package io.pragmatic.ddd.base.test2.event;

import io.pragmatic.ddd.base.test2.entity.Person;
import io.pragmatic.ddd.event.BaseDomainEvent;
import lombok.Getter;

@Getter
public class PersonUpdateEvent extends BaseDomainEvent {
    private final Long id;
    private final String name;

    public PersonUpdateEvent(String businessId, Long id, String name) {
        super(businessId);
        this.id = id;
        this.name = name;
    }

    protected PersonUpdateEvent() {
        this.id = null;
        this.name = null;
    }

    public static PersonUpdateEvent build(Person person) {
        return new PersonUpdateEvent(String.valueOf(person.getId()), person.getId(), person.getName());
    }
}
