package io.pragmatic.ddd.scenario.domain.person.event;

import io.pragmatic.ddd.scenario.domain.person.model.Person;
import io.pragmatic.ddd.event.BaseDomainEvent;
import lombok.Getter;

@Getter
public class PersonUpdateEvent extends BaseDomainEvent {
    private final Long id;
    private final String name;

    public PersonUpdateEvent(String entityId, Long id, String name) {
        super(entityId);
        this.id = id;
        this.name = name;
    }

    protected PersonUpdateEvent() {
        this.id = null;
        this.name = null;
    }

    public static PersonUpdateEvent build(Person person) {
        return new PersonUpdateEvent(String.valueOf(person.getEntityId()), person.getEntityId(), person.getName());
    }
}
