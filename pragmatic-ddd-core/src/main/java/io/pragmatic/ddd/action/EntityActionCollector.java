package io.pragmatic.ddd.action;

import org.apache.commons.lang3.tuple.MutableTriple;
import org.apache.commons.lang3.tuple.Triple;

import java.util.Arrays;
import java.util.HashMap;
import java.util.stream.Collectors;

/**
 * 实体命令收集
 *
 * @author lixiaojing10
 */
public class EntityActionCollector {
    private final HashMap<String, Action> actionHashMap = new HashMap<>();
    private final EntityAction entityAction;

    public EntityActionCollector(EntityAction entityAction) {
        this.entityAction = entityAction;
    }

    public void put(Action action) {
        if (!this.entityAction.actions().containsKey(action.getActionCode())) {
            throw new ActionException("not find action in EntityAction");
        }
        this.actionHashMap.put(action.getActionCode(),action);
    }
    /**
     * 包含所有Action
     */
    public boolean containActions(Action... actions) {
        return actionHashMap.keySet().containsAll(Arrays.stream(actions).map(Action::getActionCode)
                .toList());
    }

    /**
     * 包含任何一个Action
     */
    public boolean containAnyAction(Action... actions) {
        return Arrays.stream(actions).anyMatch(this::containAction);
    }

    /**
     * 包含指定的Action
     */
    public boolean containAction(Action action) {
        return actionHashMap.containsKey(action.getActionCode());
    }

    /**
     * 不包含指定的Action
     */
    public boolean notContainAction(Action action) {
        return !actionHashMap.containsKey(action.getActionCode());
    }

    public void clear() {
        this.actionHashMap.clear();
    }

}
