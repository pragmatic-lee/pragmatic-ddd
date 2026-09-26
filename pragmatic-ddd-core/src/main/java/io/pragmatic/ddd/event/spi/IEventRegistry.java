package io.pragmatic.ddd.event.spi;

import io.pragmatic.ddd.event.IDomainEvent;
import io.pragmatic.ddd.event.internal.model.DeliveryPolicy;

/**
 * 领域事件订阅者注册端口，支持条件、依赖与投递策略的组合注册。
 *
 * @author wizard-lee
 */
public interface IEventRegistry {

    /**
     * 注册某场景的订阅者（默认执行条件、无依赖、立即投递）。
     *
     * @param <T>            领域事件类型
     * @param subscriberCode 订阅者别名
     * @param cls            订阅的事件类型
     * @param handle         事件处理逻辑
     */
    <T extends IDomainEvent> void registerSubscriber(String subscriberCode, Class<T> cls, IHandle<T> handle);

    /**
     * 注册订阅者，携带执行条件。
     *
     * @param <T>            领域事件类型
     * @param subscriberCode 订阅者别名
     * @param cls            订阅的事件类型
     * @param handle         事件处理逻辑
     * @param condition      执行条件
     */
    <T extends IDomainEvent> void registerSubscriber(String subscriberCode, Class<T> cls, IHandle<T> handle, IExecuteCondition<T> condition);

    /**
     * 注册订阅者，携带投递策略。
     *
     * @param <T>            领域事件类型
     * @param subscriberCode 订阅者别名
     * @param cls            订阅的事件类型
     * @param handle         事件处理逻辑
     * @param policy         投递策略
     */
    <T extends IDomainEvent> void registerSubscriber(String subscriberCode, Class<T> cls, IHandle<T> handle, DeliveryPolicy policy);

    /**
     * 注册订阅者，携带执行条件与前置依赖订阅者。
     *
     * @param <T>               领域事件类型
     * @param subscriberCode    订阅者别名
     * @param cls               订阅的事件类型
     * @param handle            事件处理逻辑
     * @param condition         执行条件
     * @param dependSubscriber  前置依赖的订阅者别名
     */
    <T extends IDomainEvent> void registerSubscriber(String subscriberCode, Class<T> cls, IHandle<T> handle, IExecuteCondition<T> condition, String dependSubscriber);

    /**
     * 注册订阅者，携带执行条件与投递策略。
     *
     * @param <T>            领域事件类型
     * @param subscriberCode 订阅者别名
     * @param cls            订阅的事件类型
     * @param handle         事件处理逻辑
     * @param condition      执行条件
     * @param policy         投递策略
     */
    <T extends IDomainEvent> void registerSubscriber(String subscriberCode, Class<T> cls, IHandle<T> handle, IExecuteCondition<T> condition, DeliveryPolicy policy);

    /**
     * 注册订阅者，携带执行条件、前置依赖订阅者与投递策略。
     *
     * @param <T>               领域事件类型
     * @param subscriberCode    订阅者别名
     * @param cls               订阅的事件类型
     * @param handle            事件处理逻辑
     * @param condition         执行条件
     * @param dependSubscriber  前置依赖的订阅者别名
     * @param policy            投递策略
     */
    <T extends IDomainEvent> void registerSubscriber(String subscriberCode, Class<T> cls, IHandle<T> handle, IExecuteCondition<T> condition, String dependSubscriber, DeliveryPolicy policy);
}
