package io.pragmatic.ddd.event.spi;

import io.pragmatic.ddd.event.IDomainEvent;
import io.pragmatic.ddd.event.internal.model.DeliveryPolicy;

public interface IEventRegistry {

    <T extends IDomainEvent> void registerSubscriber(String alias, Class<T> cls, IHandle<T> handle);

    <T extends IDomainEvent> void registerSubscriber(String alias, Class<T> cls, IHandle<T> handle, IExecuteCondition<T> condition);

    <T extends IDomainEvent> void registerSubscriber(String alias, Class<T> cls, IHandle<T> handle, DeliveryPolicy policy);

    <T extends IDomainEvent> void registerSubscriber(String alias, Class<T> cls, IHandle<T> handle, IExecuteCondition<T> condition, String dependSubscriber);

    <T extends IDomainEvent> void registerSubscriber(String alias, Class<T> cls, IHandle<T> handle, IExecuteCondition<T> condition, DeliveryPolicy policy);

    <T extends IDomainEvent> void registerSubscriber(String alias, Class<T> cls, IHandle<T> handle, IExecuteCondition<T> condition, String dependSubscriber, DeliveryPolicy policy);
}
