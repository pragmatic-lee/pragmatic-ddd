package io.pragmatic.ddd.event.spi;

import io.pragmatic.ddd.event.IDomainEvent;

/**
 * 事件执行条件端口，决定某订阅者是否处理给定事件。
 *
 * @param <T> 领域事件类型
 * @author wizard-lee
 */
public interface IExecuteCondition<T extends IDomainEvent> {
    /** 返回该事件是否应当被处理。 */
    ExecuteStatus status(T t);
}
