package io.pragmatic.ddd.application.fixture;

import io.pragmatic.ddd.event.IDomainEvent;
import io.pragmatic.ddd.event.internal.model.DeliveryPolicy;
import io.pragmatic.ddd.event.spi.IEventManager;
import io.pragmatic.ddd.event.spi.IExecuteCondition;
import io.pragmatic.ddd.event.spi.IHandle;
import io.pragmatic.ddd.event.spi.ISubscriberOrderManager;

import java.util.List;
import java.util.Map;

/**
 * 试跑测试专用事件管理器夹具：仅统计事件发布次数，用于断言 Dry-run 不触发事件分发。
 */
public class CountingEventManager implements IEventManager {

    private int publishedCount = 0;

    /** 返回累计发布的事件条数。 */
    public int publishedCount() {
        return publishedCount;
    }

    @Override
    public <T extends IDomainEvent> void publish(T event) {
        publishedCount++;
    }

    @Override
    public <T extends IDomainEvent> void publish(T event, String subscriber) {
        publishedCount++;
    }

    @Override
    public <T extends IDomainEvent> void publish(T event, String subscriber, boolean onlyThis) {
        publishedCount++;
    }

    @Override
    public <T extends IDomainEvent> void publishList(List<T> events) {
        publishedCount += events.size();
    }

    @Override
    public <T extends IDomainEvent> void registerSubscriber(String subscriberCode, Class<T> cls, IHandle<T> handle) {
        // 试跑测试不涉及订阅注册
    }

    @Override
    public <T extends IDomainEvent> void registerSubscriber(String subscriberCode, Class<T> cls, IHandle<T> handle,
                                                            IExecuteCondition<T> condition) {
        // 试跑测试不涉及订阅注册
    }

    @Override
    public <T extends IDomainEvent> void registerSubscriber(String subscriberCode, Class<T> cls, IHandle<T> handle,
                                                            DeliveryPolicy policy) {
        // 试跑测试不涉及订阅注册
    }

    @Override
    public <T extends IDomainEvent> void registerSubscriber(String subscriberCode, Class<T> cls, IHandle<T> handle,
                                                            IExecuteCondition<T> condition, String dependSubscriber) {
        // 试跑测试不涉及订阅注册
    }

    @Override
    public <T extends IDomainEvent> void registerSubscriber(String subscriberCode, Class<T> cls, IHandle<T> handle,
                                                            IExecuteCondition<T> condition, DeliveryPolicy policy) {
        // 试跑测试不涉及订阅注册
    }

    @Override
    public <T extends IDomainEvent> void registerSubscriber(String subscriberCode, Class<T> cls, IHandle<T> handle,
                                                            IExecuteCondition<T> condition, String dependSubscriber,
                                                            DeliveryPolicy policy) {
        // 试跑测试不涉及订阅注册
    }

    @Override
    public void init() {
        // 试跑测试不涉及生命周期
    }

    @Override
    public void start() {
        // 试跑测试不涉及生命周期
    }

    @Override
    public void shutdown() {
        // 试跑测试不涉及生命周期
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
