package io.pragmatic.ddd.action;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * @author lixiaojing10
 */
public abstract class EntityAction {

    public static final Action NEW = Action.build("NEW", "新建");
    public static final Action DELETE = Action.build("DELETE", "删除");

    private final Map<String, Action> actionMap = new HashMap<>();

    public EntityAction() {
        this.actionMap.put(NEW.getActionCode(), NEW);
        this.actionMap.put(DELETE.getActionCode(), DELETE);
        this.registerActions();
    }

    /**
     * 子类在此方法中调用 {@link #register(Action)} 注册自定义动作。
     */
    protected abstract void registerActions();

    /**
     * 注册一个自定义动作。
     */
    protected final void register(Action action) {
        this.actionMap.put(action.getActionCode(), action);
    }

    Map<String, Action> actions() {
        return Collections.unmodifiableMap(this.actionMap);
    }
}
