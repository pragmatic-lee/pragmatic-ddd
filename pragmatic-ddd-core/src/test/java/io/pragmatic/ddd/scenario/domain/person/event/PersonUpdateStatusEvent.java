package io.pragmatic.ddd.scenario.domain.person.event;

import io.pragmatic.ddd.scenario.domain.person.model.Person;
import io.pragmatic.ddd.event.BaseDomainEvent;
import lombok.Getter;

@Getter
public class PersonUpdateStatusEvent extends BaseDomainEvent {
    private final Long id;
    private final String name;

    public PersonUpdateStatusEvent(String entityId, Long id, String name) {
        super(entityId);
        this.id = id;
        this.name = name;
    }

    protected PersonUpdateStatusEvent() {
        this.id = null;
        this.name = null;
    }

    public static PersonUpdateStatusEvent build(Person person) {
        return new PersonUpdateStatusEvent(String.valueOf(person.getEntityId()), person.getEntityId(), person.getName());
    }
}
