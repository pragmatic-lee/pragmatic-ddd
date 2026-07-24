package io.pragmatic.ddd.base.test2.event;

import io.pragmatic.ddd.base.test2.entity.Person;
import io.pragmatic.ddd.event.BaseDomainEvent;
import lombok.Getter;

@Getter
public class PersonUpdateStatusEvent extends BaseDomainEvent {
    private final Long id;
    private final String name;

    public PersonUpdateStatusEvent(String businessId, Long id, String name) {
        super(businessId);
        this.id = id;
        this.name = name;
    }

    protected PersonUpdateStatusEvent() {
        this.id = null;
        this.name = null;
    }

    public static PersonUpdateStatusEvent build(Person person) {
        return new PersonUpdateStatusEvent(String.valueOf(person.getId()), person.getId(), person.getName());
    }
}
