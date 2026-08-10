package io.pragmatic.ddd.scenario.domain.person.event;

import io.pragmatic.ddd.scenario.domain.person.model.Person;
import lombok.Getter;

/**
 * 人员解冻事件。
 *
 * @author wizard-lee
 */
@Getter
public class PersonUnfrozenEvent extends BasePersonEvent {

    public PersonUnfrozenEvent(Long personId) {
        super(personId);
    }

    protected PersonUnfrozenEvent() {
        super();
    }

    public static PersonUnfrozenEvent build(Person person) {
        return new PersonUnfrozenEvent(person.getEntityId());
    }
}
