package io.pragmatic.ddd.operation;

import java.util.Arrays;
import java.util.HashMap;

/**
 * 实体已触发操作收集器（对应设计文档 3.2：替代原 {@code action.EntityActionCollector}）。
 * <p>负责收集并校验实体在一次工作单元内已触发的 {@link EntityOperation}，
 * 构造参数与内部引用基于 {@link OperationRegistry} / {@link EntityOperation}。</p>
 */
public class TriggeredOperations {

    private final HashMap<String, EntityOperation> triggeredMap = new HashMap<>();
    private final OperationRegistry operationRegistry;

    public TriggeredOperations(OperationRegistry operationRegistry) {
        this.operationRegistry = operationRegistry;
    }

    public void put(EntityOperation operation) {
        if (!this.operationRegistry.operations().containsKey(operation.code())) {
            throw new OperationException("operation not found in OperationRegistry: " + operation.code());
        }
        this.triggeredMap.put(operation.code(), operation);
    }

    /**
     * 包含所有 Operation
     */
    public boolean containsAll(EntityOperation... operations) {
        return triggeredMap.keySet().containsAll(Arrays.stream(operations).map(EntityOperation::code)
                .toList());
    }

    /**
     * 包含任何一个 Operation
     */
    public boolean containsAny(EntityOperation... operations) {
        return Arrays.stream(operations).anyMatch(this::contains);
    }

    /**
     * 包含指定的 Operation
     */
    public boolean contains(EntityOperation operation) {
        return triggeredMap.containsKey(operation.code());
    }

    public void clear() {
        this.triggeredMap.clear();
    }
}
