package io.pragmatic.ddd.event.internal.manager;

import io.pragmatic.ddd.event.IDomainEvent;
import io.pragmatic.ddd.event.internal.model.DeliveryPolicy;
import io.pragmatic.ddd.event.internal.model.SubscriberInfo;
import io.pragmatic.ddd.event.internal.defaults.DefaultExecuteCondition;
import io.pragmatic.ddd.event.internal.defaults.SubscriberFactory;
import io.pragmatic.ddd.event.spi.*;
import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

public abstract class AbstractEventManager implements IEventManager {

    protected final String environmentName;
    protected final ConcurrentHashMap<String, Map<String, SubscriberInfo>> subscribers = new ConcurrentHashMap<>();
    private final IExecuteCondition<IDomainEvent> defaultCondition = new DefaultExecuteCondition<>();

    protected final ISubscriberOrderManager orderManager;

    protected AbstractEventManager(String environmentName, ISubscriberOrderManager orderManager) {
        this.environmentName = environmentName;
        this.orderManager = orderManager;
    }

    /**
     * 解析事件名：类名 + 环境前缀。
     * 替代原有的 getEventName() 方法，不再读取 @EventName 注解。
     */
    protected String resolveEventName(Class<?> eventType) {
        String name = eventType.getSimpleName();
        if (StringUtils.isNotBlank(this.environmentName)) {
            name = this.environmentName + "_" + name;
        }
        return name;
    }

    protected Map<String, SubscriberInfo> filterSubscriberInfoMap(String eventName) {
        Map<String, SubscriberInfo> subscriberMap = this.subscribers.get(eventName);
        if (subscriberMap != null && this.orderManager != null) {
            List<String> rootSubscribers = this.orderManager.findRootSubscribers(eventName);
            return subscriberMap.entrySet().stream()
                    .filter(s -> rootSubscribers.contains(s.getKey()))
                    .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
        }
        return Optional.ofNullable(subscriberMap).orElse(new HashMap<>());
    }

    protected <T extends IDomainEvent> SubscriberInfo findSubscriberInfo(T obj, String subscriber, String eventName) {
        Map<String, SubscriberInfo> subscriberMap = this.subscribers.get(eventName);
        if (subscriberMap == null) {
            return null;
        }
        SubscriberInfo subscriberInfo = subscriberMap.get(subscriber);
        if (subscriberInfo == null) {
            return null;
        }
        IExecuteCondition condition = subscriberInfo.getCondition();
        if (this.executeCheck(obj, condition) == ExecuteStatus.SKIP) {
            return null;
        }
        return subscriberInfo;
    }

    protected ExecuteStatus executeCheck(final IDomainEvent t, IExecuteCondition iExecuteCondition) {
        try {
            return Optional.ofNullable(iExecuteCondition).orElse(defaultCondition).status(t);
        } catch (Exception ex) {
            return ExecuteStatus.SKIP;   // 异常时视为不执行
        }
    }

    @Override
    public List<ISubscriberOrderManager.OrderEdge> findEventDependencies(String eventName) {
        return this.orderManager.getDependencyEdges(eventName);
    }

    @Override
    public Map<String, List<String>> allEvents() {
        return this.subscribers.entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        v -> v.getValue().values().stream().map(SubscriberInfo::getAlias)
                                .collect(toList()))
                );
    }

    @Override
    public <T extends IDomainEvent> void registerSubscriber(String alias, Class<T> cls, IHandle<T> handle) {
        this.doRegister(alias, SubscriberFactory.build(cls, handle), defaultCondition, "", DeliveryPolicy.IMMEDIATE);
    }

    @Override
    public <T extends IDomainEvent> void registerSubscriber(String alias, Class<T> cls, IHandle<T> handle, IExecuteCondition<T> condition) {
        this.doRegister(alias, SubscriberFactory.build(cls, handle), condition, "", DeliveryPolicy.IMMEDIATE);
    }

    @Override
    public <T extends IDomainEvent> void registerSubscriber(String alias, Class<T> cls, IHandle<T> handle, DeliveryPolicy policy) {
        this.doRegister(alias, SubscriberFactory.build(cls, handle), defaultCondition, "", policy);
    }

    @Override
    public <T extends IDomainEvent> void registerSubscriber(String alias, Class<T> cls, IHandle<T> handle, IExecuteCondition<T> condition, String dependSubscriber) {
        this.doRegister(alias, SubscriberFactory.build(cls, handle), condition, dependSubscriber, DeliveryPolicy.IMMEDIATE);
    }

    @Override
    public <T extends IDomainEvent> void registerSubscriber(String alias, Class<T> cls, IHandle<T> handle, IExecuteCondition<T> condition, DeliveryPolicy policy) {
        this.doRegister(alias, SubscriberFactory.build(cls, handle), condition, "", policy);
    }

    @Override
    public <T extends IDomainEvent> void registerSubscriber(String alias, Class<T> cls, IHandle<T> handle, IExecuteCondition<T> condition, String dependSubscriber, DeliveryPolicy policy) {
        this.doRegister(alias, SubscriberFactory.build(cls, handle), condition, dependSubscriber, policy);
    }

    protected void doRegister(String alias, ISubscriber subscriber,
                              IExecuteCondition condition,
                              String dependSubscriber,
                              DeliveryPolicy policy) {
        String eventName = resolveEventName(subscriber.subscribedToEventType());
        if (this.subscribers.containsKey(eventName)) {
            Map<String, SubscriberInfo> stringISubscriberMap = this.subscribers.get(eventName);
            if (stringISubscriberMap.containsKey(alias)) {
                throw new IllegalArgumentException(alias + " is duplication");
            }
            this.subscribers.get(eventName).put(alias,
                    new SubscriberInfo(subscriber, alias, condition, policy));
        } else {
            Map<String, SubscriberInfo> subscriberMap = new HashMap<>();
            subscriberMap.put(alias, new SubscriberInfo(subscriber, alias, condition, policy));
            this.subscribers.put(eventName, subscriberMap);
        }
        if (this.orderManager != null) {
            this.orderManager.registerDependency(eventName, alias,
                    StringUtils.isBlank(dependSubscriber) ? ISubscriberOrderManager.ROOT_ALIAS : dependSubscriber);
        }
    }

    // IEventPublisher - abstract, subclasses implement
    @Override
    public abstract <T extends IDomainEvent> void publish(T event);

    @Override
    public abstract <T extends IDomainEvent> void publish(T event, String subscriber);

    @Override
    public <T extends IDomainEvent> void publish(T event, String subscriber, boolean onlyThis) {
        // default empty, subclasses override if needed
    }

    @Override
    public <T extends IDomainEvent> void publishList(List<T> events) {
        for (T event : events) {
            publish(event);
        }
    }

    // IEventLifecycle
    @Override
    public void init() {
    }

    @Override
    public void start() {
    }

    @Override
    public void shutdown() {
    }
}
