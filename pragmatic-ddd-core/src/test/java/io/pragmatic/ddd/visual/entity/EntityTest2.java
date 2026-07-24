package io.pragmatic.ddd.visual.entity;

import io.pragmatic.ddd.action.EntityAction;
import io.pragmatic.ddd.base.*;

public class EntityTest2 extends AbstractEntity<Long> {

    private String name;

    public EntityTest2(Long id, String name) {
        this.setNewEntity(true);
        this.setId(id);
        this.setName(name);
    }

    public void updateForUse(String newName) {
        this.recordAction(EntityTest2Action.testAction);
        this.setName(newName);
    }

    @Override
    protected BrokenRuleMessage getBrokenRuleMessages() {
        return new EntityTest2BrokenRuleMessage();
    }

    @Override
    public EntityAction entityActions() {
        return new EntityTest2Action();
    }

    public String getName() {
        return name;
    }

    protected void setName(String name) {
        this.name = name;
    }
}
