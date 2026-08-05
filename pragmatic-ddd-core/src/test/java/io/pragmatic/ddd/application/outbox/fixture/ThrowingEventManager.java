package io.pragmatic.ddd.application.outbox.fixture;

import io.pragmatic.ddd.event.IDomainEvent;
import io.pragmatic.ddd.event.internal.model.DeliveryPolicy;
import io.pragmatic.ddd.event.spi.IEventManager;
import io.pragmatic.ddd.event.spi.IExecuteCondition;
import io.pragmatic.ddd.event.spi.IHandle;
import io.pragmatic.ddd.event.spi.ISubscriberOrderManager;

import java.util.List;
import java.util.Map;

/**
 * 发布即抛异常的事件管理器测试夹具：用于模拟 eager 路径"MQ 发送失败"。
 *
 * @author wizard-lee
 */
public class ThrowingEventManager implements IEventManager {

    @Override
    public <T extends IDomainEvent> void publish(T event) {
        throw new IllegalStateException("publish failed");
    }

    @Override
    public <T extends IDomainEvent> void publish(T event, String subscriber) {
        throw new IllegalStateException("publish failed");
    }

    @Override
    public <T extends IDomainEvent> void publish(T event, String subscriber, boolean onlyThis) {
        throw new IllegalStateException("publish failed");
    }

    @Override
    public <T extends IDomainEvent> void publishList(List<T> events) {
        throw new IllegalStateException("publish failed");
    }

    @Override
    public <T extends IDomainEvent> void registerSubscriber(String subscriberCode, Class<T> cls, IHandle<T> handle) {
        // 测试夹具不涉及注册
    }

    @Override
    public <T extends IDomainEvent> void registerSubscriber(String subscriberCode, Class<T> cls, IHandle<T> handle,
                                                            IExecuteCondition<T> condition) {
        // 测试夹具不涉及注册
    }

    @Override
    public <T extends IDomainEvent> void registerSubscriber(String subscriberCode, Class<T> cls, IHandle<T> handle,
                                                            DeliveryPolicy policy) {
        // 测试夹具不涉及注册
    }

    @Override
    public <T extends IDomainEvent> void registerSubscriber(String subscriberCode, Class<T> cls, IHandle<T> handle,
                                                            IExecuteCondition<T> condition, String dependSubscriber) {
        // 测试夹具不涉及注册
    }

    @Override
    public <T extends IDomainEvent> void registerSubscriber(String subscriberCode, Class<T> cls, IHandle<T> handle,
                                                            IExecuteCondition<T> condition, DeliveryPolicy policy) {
        // 测试夹具不涉及注册
    }

    @Override
    public <T extends IDomainEvent> void registerSubscriber(String subscriberCode, Class<T> cls, IHandle<T> handle,
                                                            IExecuteCondition<T> condition, String dependSubscriber,
                                                            DeliveryPolicy policy) {
        // 测试夹具不涉及注册
    }

    @Override
    public void init() {
        // 测试夹具不涉及生命周期
    }

    @Override
    public void start() {
        // 测试夹具不涉及生命周期
    }

    @Override
    public void shutdown() {
        // 测试夹具不涉及生命周期
    }

    @Override
    public Map<String, List<String>> allEvents() {
        return Map.of();
    }

    @Override
    public List<ISubscriberOrderManager.OrderEdge> findEventDependencies(String eventName) {
        return List.of();
    }
}
