package io.pragmatic.ddd.scenario.domain.person.event;

import io.pragmatic.ddd.scenario.domain.person.model.Person;
import lombok.Getter;

/**
 * 人员冻结事件。
 *
 * @author wizard-lee
 */
@Getter
public class PersonFrozenEvent extends BasePersonEvent {

    private final String reason;

    public PersonFrozenEvent(Long personId, String reason) {
        super(personId);
        this.reason = reason;
    }

    protected PersonFrozenEvent() {
        super();
        this.reason = null;
    }

    public static PersonFrozenEvent build(Person person, String reason) {
        return new PersonFrozenEvent(person.getEntityId(), reason);
    }
}
