package io.pragmatic.ddd.scenario.domain.person.event;

import io.pragmatic.ddd.scenario.domain.person.model.Person;
import lombok.Getter;

/**
 * 人员归档（软删）事件。
 *
 * @author wizard-lee
 */
@Getter
public class PersonArchivedEvent extends BasePersonEvent {

    public PersonArchivedEvent(Long personId) {
        super(personId);
    }

    protected PersonArchivedEvent() {
        super();
    }

    public static PersonArchivedEvent build(Person person) {
        return new PersonArchivedEvent(person.getEntityId());
    }
}
