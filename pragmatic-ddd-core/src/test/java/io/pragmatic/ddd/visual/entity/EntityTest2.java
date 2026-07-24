package io.pragmatic.ddd.visual.entity;

import io.pragmatic.ddd.base.*;
import io.pragmatic.ddd.operation.EntityTest2Operations;
import io.pragmatic.ddd.operation.OperationRegistry;

public class EntityTest2 extends AbstractEntity<Long> {

    private String name;

    public EntityTest2(Long id, String name) {
        this.setNewEntity(true);
        this.setEntityId(id);
        this.setName(name);
    }

    public void updateForUse(String newName) {
        this.recordOperation(EntityTest2Operations.TEST);
        this.setName(newName);
    }

    @Override
    protected BrokenRuleMessage getBrokenRuleMessages() {
        return new EntityTest2BrokenRuleMessage();
    }

    @Override
    public OperationRegistry entityOperations() {
        return new EntityTest2Operations();
    }

    public String getName() {
        return name;
    }

    protected void setName(String name) {
        this.name = name;
    }
}
