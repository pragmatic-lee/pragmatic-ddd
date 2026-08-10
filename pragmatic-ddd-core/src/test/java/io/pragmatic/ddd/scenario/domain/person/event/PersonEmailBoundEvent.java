package io.pragmatic.ddd.scenario.domain.person.event;

import io.pragmatic.ddd.scenario.domain.person.model.Person;
import lombok.Getter;

/**
 * 人员邮箱绑定事件。
 *
 * @author wizard-lee
 */
@Getter
public class PersonEmailBoundEvent extends BasePersonEvent {

    private final String email;

    public PersonEmailBoundEvent(Long personId, String email) {
        super(personId);
        this.email = email;
    }

    protected PersonEmailBoundEvent() {
        super();
        this.email = null;
    }

    public static PersonEmailBoundEvent build(Person person, String email) {
        return new PersonEmailBoundEvent(person.getEntityId(), email);
    }
}
