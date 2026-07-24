package io.pragmatic.ddd.event.spi;

import io.pragmatic.ddd.event.IDomainEvent;

public interface IExecuteCondition<T extends IDomainEvent> {
    ExecuteStatus status(T t);
}
