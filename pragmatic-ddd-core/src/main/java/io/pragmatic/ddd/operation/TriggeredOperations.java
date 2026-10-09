package io.pragmatic.ddd.operation;

import java.util.Arrays;
import java.util.Optional;

/**
 * 实体操作收集器（单值）。
 * <p>一次工作单元只允许一个因果操作：相同 code 重复记录视为幂等，
 * 出现第二个不同 code 立即抛出 {@link MultipleOperationsException}。</p>
 *
 * @author wizard-lee
 */
public class TriggeredOperations {

    private final OperationRegistry operationRegistry;
    private EntityOperation current;

    public TriggeredOperations(OperationRegistry operationRegistry) {
        this.operationRegistry = operationRegistry;
    }

    /** 记录本次工作单元的因果操作；相同 code 幂等，不同 code 抛 MultipleOperationsException。 */
    public void record(EntityOperation operation) {
        if (!this.operationRegistry.operations().containsKey(operation.code())) {
            throw new OperationException("operation not found in OperationRegistry: " + operation.code());
        }
        if (this.current == null) {
            this.current = operation;
            return;
        }
        if (!this.current.code().equals(operation.code())) {
            throw new MultipleOperationsException(this.current.code(), operation.code());
        }
    }

    /** 判断本次工作单元的因果操作是否为指定操作。 */
    public boolean contains(EntityOperation operation) {
        if (this.current == null) {
            return false;
        }
        return this.current.code().equals(operation.code());
    }

    /** 返回本次工作单元的因果操作，未记录时为空。 */
    public Optional<EntityOperation> current() {
        return Optional.ofNullable(this.current);
    }

    /** 判断本次工作单元的因果操作是否属于指定操作之一。 */
    public boolean containsAny(EntityOperation... operations) {
        if (this.current == null) {
            return false;
        }
        String currentCode = this.current.code();
        return Arrays.stream(operations)
                .map(EntityOperation::code)
                .anyMatch(currentCode::equals);
    }

    /**
     * 判断本次工作单元的因果操作是否存在且不属于指定操作集合。
     * 未记录操作时返回 false（保守方向：不激活依赖它的规则）。
     */
    public boolean containsExceptOperation(EntityOperation... operations) {
        if (this.current == null) {
            return false;
        }
        String currentCode = this.current.code();
        return Arrays.stream(operations)
                .map(EntityOperation::code)
                .noneMatch(currentCode::equals);
    }

    /** 清空当前因果操作。 */
    public void clear() {
        this.current = null;
    }
}
