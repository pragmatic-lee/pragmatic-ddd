package io.pragmatic.ddd.operation;

import java.util.Arrays;
import java.util.HashMap;

/**
 * 实体已触发操作收集器（对应设计文档 3.2：替代原 {@code action.EntityActionCollector}）。
 * <p>对外 API（put / containAction / containActions / containAnyAction / notContainAction / clear）
 * 保持不变，仅构造参数与内部引用由 {@code EntityAction} / {@code Action} 切换为
 * {@link OperationRegistry} / {@link EntityOperation}。</p>
 */
public class TriggeredOperations {

    private final HashMap<String, EntityOperation> actionHashMap = new HashMap<>();
    private final OperationRegistry operationRegistry;

    public TriggeredOperations(OperationRegistry operationRegistry) {
        this.operationRegistry = operationRegistry;
    }

    public void put(EntityOperation action) {
        if (!this.operationRegistry.operations().containsKey(action.code())) {
            throw new OperationException("not find action in OperationRegistry");
        }
        this.actionHashMap.put(action.code(), action);
    }

    /**
     * 包含所有 Operation
     */
    public boolean containActions(EntityOperation... actions) {
        return actionHashMap.keySet().containsAll(Arrays.stream(actions).map(EntityOperation::code)
                .toList());
    }

    /**
     * 包含任何一个 Operation
     */
    public boolean containAnyAction(EntityOperation... actions) {
        return Arrays.stream(actions).anyMatch(this::containAction);
    }

    /**
     * 包含指定的 Operation
     */
    public boolean containAction(EntityOperation action) {
        return actionHashMap.containsKey(action.code());
    }

    /**
     * 不包含指定的 Operation
     */
    public boolean notContainAction(EntityOperation action) {
        return !actionHashMap.containsKey(action.code());
    }

    public void clear() {
        this.actionHashMap.clear();
    }
}
