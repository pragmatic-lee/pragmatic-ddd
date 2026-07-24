package io.pragmatic.ddd.base.test2.action;

import io.pragmatic.ddd.action.Action;
import io.pragmatic.ddd.action.EntityAction;

public class PersonAction extends EntityAction {

    public static final Action START_ACTION = Action.build("startAction", "启动");
    public static final Action END_ACTION = Action.build("endAction", "停止");
    public static final Action UPDATE_ACTION = Action.build("updateAction", "更新");
    public static final Action UPDATE_STATUS_ACTION = Action.build("updateAction", "更新状态");


    public static final EntityAction INSTANCE = new PersonAction();

    @Override
    protected void registerActions() {

        this.register(START_ACTION);
        this.register(END_ACTION);
        this.register(UPDATE_ACTION);
        this.register(UPDATE_STATUS_ACTION);

    }
}
