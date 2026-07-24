package io.pragmatic.ddd.visual.entity;

import io.pragmatic.ddd.action.Action;
import io.pragmatic.ddd.action.EntityAction;

public class EntityTest2Action extends EntityAction {

    public static Action testAction = Action.build("TestAction");

    @Override
    protected void registerActions() {
        this.register(testAction);
    }
}
