package io.pragmatic.ddd.base.test2.event;

import io.pragmatic.ddd.base.test2.entity.Person;
import io.pragmatic.ddd.event.BaseDomainEvent;
import lombok.Getter;

@Getter
public class PersonInitEvent extends BaseDomainEvent {
    private final Long id;
    private final String name;

    public PersonInitEvent(String entityId, Long id, String name) {
        super(entityId);
        this.id = id;
        this.name = name;
    }

    protected PersonInitEvent() {
        this.id = null;
        this.name = null;
    }

    public static PersonInitEvent build(Person person) {
        return new PersonInitEvent(String.valueOf(person.getEntityId()), person.getEntityId(), person.getName());
    }
}
