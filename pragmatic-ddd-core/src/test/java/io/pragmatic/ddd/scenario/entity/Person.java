package io.pragmatic.ddd.scenario.entity;

import io.pragmatic.ddd.base.*;
import io.pragmatic.ddd.operation.OperationRegistry;
import io.pragmatic.ddd.scenario.PersonOperations;
import io.pragmatic.ddd.scenario.boxvalueobject.PersonInitData;
import io.pragmatic.ddd.scenario.boxvalueobject.PersonUpdateData;
import io.pragmatic.ddd.scenario.entity.enums.Status;
import io.pragmatic.ddd.scenario.event.PersonInitEvent;
import io.pragmatic.ddd.scenario.event.PersonUpdateEvent;
import io.pragmatic.ddd.scenario.event.PersonUpdateStatusEvent;
import io.pragmatic.ddd.scenario.rule.PersonBrokenRuleRegistry;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter(AccessLevel.PROTECTED)
public class Person extends AggregateRoot<Long> {

    private String name;
    private String age;
    private String email;
    private String phone;
    private Status status;
    private Date createdTime;
    private Date updatedTime;


    public Person(PersonInitData personInitData) {
        this.setEntityId(personInitData.getId());
        this.markNew();
        PersonSetter.init(this, personInitData);
        this.recordOperation(PersonOperations.NEW);
        this.collectEvent(PersonInitEvent.build(this));

    }

    /**
     * 更新基础信息
     */
    public void update(PersonUpdateData personUpdateData) {
        PersonSetter.updateSet(this, personUpdateData);
        this.recordOperation(PersonOperations.UPDATE);
        this.collectEvent(PersonUpdateEvent.build(this));

    }

    /**
     * 更新状态
     */
    public void updateStatus(Status status) {
        this.setStatus(status);
        this.setUpdatedTime(new Date());
        this.recordOperation(PersonOperations.UPDATE_STATUS);
        this.collectEvent(PersonUpdateStatusEvent.build(this));


    }

    @Override
    protected BrokenRuleRegistry brokenRuleRegistry() {
        return PersonBrokenRuleRegistry.INSTANCE;
    }

    @Override
    protected OperationRegistry operationRegistry() {
        return PersonOperations.INSTANCE;
    }
}
