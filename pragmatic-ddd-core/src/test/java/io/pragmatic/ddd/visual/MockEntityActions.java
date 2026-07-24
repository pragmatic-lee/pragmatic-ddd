package io.pragmatic.ddd.visual;

import io.pragmatic.ddd.action.Action;
import io.pragmatic.ddd.action.EntityAction;

public class MockEntityActions extends EntityAction {

    public static final Action showNameAction = Action.build("showNameAction", "测试Action");

    public static final EntityAction  action = new MockEntityActions();

    @Override
    protected void registerActions() {

        this.register(showNameAction);

    }
}
