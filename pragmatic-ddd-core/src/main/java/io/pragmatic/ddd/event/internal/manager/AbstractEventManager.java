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

/**
 * 事件管理器抽象基类，提供订阅者注册、发布与生命周期的通用骨架。
 *
 * @author wizard-lee
 */
public abstract class AbstractEventManager implements IEventManager {

    protected final ConcurrentHashMap<String, Map<String, SubscriberInfo>> subscribers = new ConcurrentHashMap<>();
    private final IExecuteCondition<IDomainEvent> defaultCondition = new DefaultExecuteCondition<>();

    protected final ISubscriberOrderManager orderManager;

    protected AbstractEventManager(ISubscriberOrderManager orderManager) {
        this.orderManager = orderManager;
    }

    /** 返回某事件的根订阅者映射（排除存在前置依赖的非根订阅者）。 */
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

    /** 按别名查找某事件的订阅者信息，执行条件判定为跳过时返回 null。 */
    protected <T extends IDomainEvent> SubscriberInfo findSubscriberInfo(T obj, String subscriber, String eventName) {
        Map<String, SubscriberInfo> subscriberMap = this.subscribers.get(eventName);
        if (subscriberMap == null) {
            return null;
        }
        SubscriberInfo subscriberInfo = subscriberMap.get(subscriber);
        if (subscriberInfo == null) {
            return null;
        }
        // 第一重：订阅者级开关（别名即入参 subscriber）
        if (this.switchCheck(subscriber, subscriberInfo.condition()) == ExecuteStatus.SKIP) {
            return null;
        }
        // 第二重：事件级条件（基于事件内容决定是否执行）
        IExecuteCondition condition = subscriberInfo.condition();
        if (this.executeCheck(obj, condition) == ExecuteStatus.SKIP) {
            return null;
        }
        return subscriberInfo;
    }

    /** 执行条件判定：条件为空时回退默认条件，异常时视为跳过。 */
    protected ExecuteStatus executeCheck(final IDomainEvent t, IExecuteCondition iExecuteCondition) {
        try {
            return Optional.ofNullable(iExecuteCondition).orElse(defaultCondition).status(t);
        } catch (Exception ex) {
            return ExecuteStatus.SKIP;   // 异常时视为不执行
        }
    }

    /** 订阅者级开关判定：条件为空时回退默认条件，异常时视为跳过。 */
    protected ExecuteStatus switchCheck(final String alias, IExecuteCondition iExecuteCondition) {
        try {
            return Optional.ofNullable(iExecuteCondition).orElse(defaultCondition).switchStatus(alias);
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
                        v -> v.getValue().values().stream().map(SubscriberInfo::alias)
                                .collect(toList()))
                );
    }

    @Override
    public <T extends IDomainEvent> void registerSubscriber(String subscriberCode, Class<T> cls, IHandle<T> handle) {
        this.doRegister(subscriberCode, SubscriberFactory.build(cls, handle), defaultCondition, null, DeliveryPolicy.IMMEDIATE);
    }

    @Override
    public <T extends IDomainEvent> void registerSubscriber(String subscriberCode, Class<T> cls, IHandle<T> handle, IExecuteCondition<T> condition) {
        this.doRegister(subscriberCode, SubscriberFactory.build(cls, handle), condition, null, DeliveryPolicy.IMMEDIATE);
    }

    @Override
    public <T extends IDomainEvent> void registerSubscriber(String subscriberCode, Class<T> cls, IHandle<T> handle, DeliveryPolicy policy) {
        this.doRegister(subscriberCode, SubscriberFactory.build(cls, handle), defaultCondition, null, policy);
    }

    @Override
    public <T extends IDomainEvent> void registerSubscriber(String subscriberCode, Class<T> cls, IHandle<T> handle, IExecuteCondition<T> condition, String dependSubscriber) {
        this.doRegister(subscriberCode, SubscriberFactory.build(cls, handle), condition, dependSubscriber, DeliveryPolicy.IMMEDIATE);
    }

    @Override
    public <T extends IDomainEvent> void registerSubscriber(String subscriberCode, Class<T> cls, IHandle<T> handle, IExecuteCondition<T> condition, DeliveryPolicy policy) {
        this.doRegister(subscriberCode, SubscriberFactory.build(cls, handle), condition, null, policy);
    }

    @Override
    public <T extends IDomainEvent> void registerSubscriber(String subscriberCode, Class<T> cls, IHandle<T> handle, IExecuteCondition<T> condition, String dependSubscriber, DeliveryPolicy policy) {
        this.doRegister(subscriberCode, SubscriberFactory.build(cls, handle), condition, dependSubscriber, policy);
    }

    /** 完成单个订阅者的注册与依赖登记。 */
    protected void doRegister(String subscriberCode, ISubscriber subscriber,
                              IExecuteCondition condition,
                              String dependSubscriber,
                              DeliveryPolicy policy) {
        String alias = subscriberCode;
        String dependAlias = dependSubscriber == null ? "" : dependSubscriber;
        String eventName = subscriber.subscribedToEventType().getSimpleName();
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
                    StringUtils.isBlank(dependAlias) ? ISubscriberOrderManager.ROOT_ALIAS : dependAlias);
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
