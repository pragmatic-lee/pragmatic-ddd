package io.pragmatic.ddd.scenario.domain.person.event;

import io.pragmatic.ddd.scenario.domain.person.model.Person;
import lombok.Getter;

import java.util.List;

/**
 * 人员标签变更事件。
 *
 * @author wizard-lee
 */
@Getter
public class PersonTaggedEvent extends BasePersonEvent {

    private final List<String> tags;

    public PersonTaggedEvent(Long personId, List<String> tags) {
        super(personId);
        this.tags = tags;
    }

    protected PersonTaggedEvent() {
        super();
        this.tags = null;
    }

    public static PersonTaggedEvent build(Person person, List<String> tags) {
        return new PersonTaggedEvent(person.getEntityId(), tags);
    }
}
