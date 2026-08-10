package io.pragmatic.ddd.scenario.domain.person.event;

import io.pragmatic.ddd.scenario.domain.person.model.Person;
import lombok.Getter;

/**
 * 人员手机号绑定事件。
 *
 * @author wizard-lee
 */
@Getter
public class PersonPhoneBoundEvent extends BasePersonEvent {

    private final String phone;

    public PersonPhoneBoundEvent(Long personId, String phone) {
        super(personId);
        this.phone = phone;
    }

    protected PersonPhoneBoundEvent() {
        super();
        this.phone = null;
    }

    public static PersonPhoneBoundEvent build(Person person, String phone) {
        return new PersonPhoneBoundEvent(person.getEntityId(), phone);
    }
}
