package io.pragmatic.ddd.event.internal.defaults;

import io.pragmatic.ddd.event.IDomainEvent;
import io.pragmatic.ddd.event.spi.ExecuteStatus;
import io.pragmatic.ddd.event.spi.IExecuteCondition;

/**
 * 默认事件订阅执行条件，恒返回执行。
 *
 * @author wizard-lee
 */
public class DefaultExecuteCondition<T extends IDomainEvent> implements IExecuteCondition<T> {
    @Override
    public ExecuteStatus status(T t1) {
        return ExecuteStatus.EXECUTE;
    }
}
