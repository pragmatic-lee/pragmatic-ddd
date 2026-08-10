package io.pragmatic.ddd.scenario.domain.person.model;

import io.pragmatic.ddd.base.*;
import io.pragmatic.ddd.operation.OperationException;
import io.pragmatic.ddd.operation.OperationRegistry;
import io.pragmatic.ddd.scenario.domain.person.operation.PersonOperations;
import io.pragmatic.ddd.scenario.domain.person.param.PersonInitData;
import io.pragmatic.ddd.scenario.domain.person.param.PersonUpdateData;
import io.pragmatic.ddd.scenario.domain.person.model.enums.GenderEnum;
import io.pragmatic.ddd.scenario.domain.person.model.enums.Status;
import io.pragmatic.ddd.scenario.domain.person.model.valueobject.Address;
import io.pragmatic.ddd.scenario.domain.person.event.PersonInitEvent;
import io.pragmatic.ddd.scenario.domain.person.event.PersonUpdateEvent;
import io.pragmatic.ddd.scenario.domain.person.event.PersonUpdateStatusEvent;
import io.pragmatic.ddd.scenario.domain.person.event.PersonFrozenEvent;
import io.pragmatic.ddd.scenario.domain.person.event.PersonUnfrozenEvent;
import io.pragmatic.ddd.scenario.domain.person.event.PersonEmailBoundEvent;
import io.pragmatic.ddd.scenario.domain.person.event.PersonPhoneBoundEvent;
import io.pragmatic.ddd.scenario.domain.person.event.PersonDeptAssignedEvent;
import io.pragmatic.ddd.scenario.domain.person.event.PersonTaggedEvent;
import io.pragmatic.ddd.scenario.domain.person.event.PersonArchivedEvent;
import io.pragmatic.ddd.scenario.domain.person.rule.PersonBrokenRuleRegistry;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter(AccessLevel.PROTECTED)
public class Person extends AggregateRoot<Long> {

    private String name;
    private GenderEnum gender;
    private String age;
    private String idCard;
    private String email;
    private String phone;
    private String avatarUrl;
    private Long departmentId;
    private String position;
    private String employeeNo;
    private Status status;
    private List<String> tags;
    private int level;
    private Address address;


    public Person(PersonInitData personInitData) {
        this.setEntityId(personInitData.getId());
        this.markNew();
        PersonSetter.init(this, personInitData);
        this.markCreated();
        this.recordOperation(PersonOperations.NEW);
        this.collectEvent(PersonInitEvent.build(this));

    }

    /**
     * 更新基础资料。
     */
    public void update(PersonUpdateData personUpdateData) {
        PersonSetter.updateSet(this, personUpdateData);
        this.markModified();
        this.recordOperation(PersonOperations.UPDATE);
        this.collectEvent(PersonUpdateEvent.build(this));

    }

    /**
     * 更新状态（保留的兼容入口，内部委托 changeStatus）。
     */
    public void updateStatus(Status status) {
        this.changeStatus(status, "legacy-update-status");
    }

    /**
     * 按状态机变更生命周期状态。
     */
    public void changeStatus(Status target, String reason) {
        if (!this.canTransitionTo(target)) {
            throw new OperationException("不允许的状态变更: " + this.status + " -> " + target);
        }
        this.setStatus(target);
        this.markModified();
        this.recordOperation(PersonOperations.UPDATE_STATUS);
        this.collectEvent(PersonUpdateStatusEvent.build(this));
    }

    /**
     * 冻结。
     */
    public void freeze(String reason) {
        this.markModified();
        this.changeStatus(Status.FROZEN, reason);
        this.recordOperation(PersonOperations.FREEZE);
        this.collectEvent(PersonFrozenEvent.build(this, reason));
    }

    /**
     * 解冻。
     */
    public void unfreeze() {
        this.markModified();
        this.changeStatus(Status.ACTIVE, "unfreeze");
        this.recordOperation(PersonOperations.UNFREEZE);
        this.collectEvent(PersonUnfrozenEvent.build(this));
    }

    /**
     * 绑定邮箱。
     */
    public void bindEmail(String email) {
        this.setEmail(email);
        this.markModified();
        this.recordOperation(PersonOperations.BIND_EMAIL);
        this.collectEvent(PersonEmailBoundEvent.build(this, email));
    }

    /**
     * 绑定手机号。
     */
    public void bindPhone(String phone) {
        this.setPhone(phone);
        this.markModified();
        this.recordOperation(PersonOperations.BIND_PHONE);
        this.collectEvent(PersonPhoneBoundEvent.build(this, phone));
    }

    /**
     * 归属部门变更。
     */
    public void assignDepartment(Long departmentId, String position) {
        this.setDepartmentId(departmentId);
        this.setPosition(position);
        this.markModified();
        this.recordOperation(PersonOperations.ASSIGN_DEPT);
        this.collectEvent(PersonDeptAssignedEvent.build(this, departmentId, position));
    }

    /**
     * 打标签。
     */
    public void tag(List<String> tags) {
        if (this.tags == null) {
            this.tags = new ArrayList<>();
        }
        this.tags.addAll(tags);
        this.markModified();
        this.recordOperation(PersonOperations.TAG);
        this.collectEvent(PersonTaggedEvent.build(this, tags));
    }

    /**
     * 归档（软删）。
     */
    public void archive() {
        this.setStatus(Status.ARCHIVED);
        this.setEntityDelete(true);
        this.markModified();
        this.recordOperation(PersonOperations.ARCHIVE);
        this.collectEvent(PersonArchivedEvent.build(this));
    }

    private boolean canTransitionTo(Status target) {
        if (this.status == null) {
            return true;
        }
        if (this.status == Status.ARCHIVED) {
            return false;
        }
        if (this.status == Status.FROZEN) {
            return target == Status.ACTIVE;
        }
        return true;
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
