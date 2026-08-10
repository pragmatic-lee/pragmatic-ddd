package io.pragmatic.ddd.scenario.domain.person.event;

import io.pragmatic.ddd.scenario.domain.person.model.Person;
import lombok.Getter;

/**
 * 人员归属部门变更事件。
 *
 * @author wizard-lee
 */
@Getter
public class PersonDeptAssignedEvent extends BasePersonEvent {

    private final Long departmentId;
    private final String position;

    public PersonDeptAssignedEvent(Long personId, Long departmentId, String position) {
        super(personId);
        this.departmentId = departmentId;
        this.position = position;
    }

    protected PersonDeptAssignedEvent() {
        super();
        this.departmentId = null;
        this.position = null;
    }

    public static PersonDeptAssignedEvent build(Person person, Long departmentId, String position) {
        return new PersonDeptAssignedEvent(person.getEntityId(), departmentId, position);
    }
}
