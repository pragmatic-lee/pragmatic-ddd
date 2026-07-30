package io.pragmatic.ddd.event.internal.manager;

import io.pragmatic.ddd.event.IDomainEvent;
import io.pragmatic.ddd.event.internal.model.DeliveryPolicy;
import io.pragmatic.ddd.event.internal.model.SubscribeData;
import io.pragmatic.ddd.event.internal.model.SubscriberInfo;
import io.pragmatic.ddd.event.internal.subscriber.AbstractEventSubscriber;
import io.pragmatic.ddd.event.spi.ExecuteStatus;
import io.pragmatic.ddd.event.spi.IEventSerializer;
import io.pragmatic.ddd.event.spi.ISubscriberOrderManager;
import io.pragmatic.ddd.event.spi.ITopicResolver;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 基于 MQ 的事件管理器抽象基类，扩展发布到消息中间件的能力。
 *
 * @author wizard-lee
 */
public abstract class AbstractMQEventManager extends AbstractEventManager {

    private final IEventSerializer serializer;
    private final ITopicResolver topicResolver;
    private final Set<String> initializedTopics = new HashSet<>();

    protected AbstractMQEventManager(String environmentName,
                                     ISubscriberOrderManager orderManager,
                                     IEventSerializer serializer,
                                     ITopicResolver topicResolver) {
        super(environmentName, orderManager);
        this.serializer = serializer;
        this.topicResolver = topicResolver;
    }

    // ══════════════════════════════════════════════
    // 子类需实现的抽象方法
    // ══════════════════════════════════════════════

    /**
     * 初始化一批 Topic 的 MQ 通道（Producer/Consumer）。
     * 框架保证同一 topic 不会重复传入此方法。
     */
    protected abstract void initializeTopics(Set<String> topics);

    /**
     * 发送一条 MQ 消息。
     * 子类将 SubscribeData 包装为 MQ 特定消息并发送。
     *
     * @param data  订阅数据（含序列化事件体、订阅者名、投递策略）
     * @param event 原始事件（子类可获取 entityId 等）
     * @param topic 目标 topic
     */
    protected abstract <T extends IDomainEvent> void sendMessage(
            SubscribeData data, T event, String topic);

    // ══════════════════════════════════════════════
    // 三个 publish 重载的默认实现
    // ══════════════════════════════════════════════

    /**
     * 全量发布：遍历所有根订阅者，逐条发送消息。
     */
    @Override
    public <T extends IDomainEvent> void publish(T obj) {
        List<SubscribeData> list = this.buildSubscribeDataList(obj);
        for (SubscribeData s : list) {
            String topic = this.getTopicName(obj, s.getName());
            sendMessage(s, obj, topic);
        }
    }

    @Override
    public <T extends IDomainEvent> void publish(T obj, String subscriber) {
        publish(obj, subscriber, false);
    }

    /**
     * 发布给指定订阅者。
     */
    @Override
    public <T extends IDomainEvent> void publish(T obj, String subscriber, boolean onlyThis) {
        SubscribeData data = this.buildSubscribeData(obj, subscriber, onlyThis);
        if (data == null) {
            return;
        }
        String topic = this.getTopicName(obj, subscriber);
        sendMessage(data, obj, topic);
    }

    // ══════════════════════════════════════════════
    // 生命周期：通道初始化
    // ══════════════════════════════════════════════

    /**
     * 批量初始化所有 Topic 通道。
     * 基于 ITopicResolver.getAllTopics() 获取全量 topic 列表。
     * 应用启动时调用，取代逐个 registerDomainEvent 的模式。
     */
    public void initTopics() {
        Set<String> allTopics = topicResolver.getAllTopics();
        Set<String> newTopics = new HashSet<>(allTopics);
        newTopics.removeAll(this.initializedTopics);
        if (newTopics.isEmpty()) {
            return;
        }
        initializeTopics(newTopics);
        this.initializedTopics.addAll(newTopics);
    }


    // ══════════════════════════════════════════════
    // 序列化 helper
    // ══════════════════════════════════════════════

    /**
     * 将 SubscribeData 序列化为 byte[]。
     * 子类在 sendMessage 中调用此方法，无需自行处理 JSON 序列化。
     */
    protected byte[] serializeSubscribeData(SubscribeData data) {
        return serializer.serialize(data).getBytes(StandardCharsets.UTF_8);
    }

    // ══════════════════════════════════════════════
    // Topic 解析
    // ══════════════════════════════════════════════

    /**
     * 获取事件类型对应的 topic。
     * 委托给 ITopicResolver.resolveForType()。
     */
    protected <T extends IDomainEvent> String getTopicName(Class<T> eventType) {
        return topicResolver.resolveForType(eventType);
    }

    /**
     * 获取事件实例在指定订阅者下的 topic。
     * 委托给 ITopicResolver.resolveForEvent()。
     */
    protected <T extends IDomainEvent> String getTopicName(T event, String subscriber) {
        return topicResolver.resolveForEvent(event, subscriber);
    }

    // ══════════════════════════════════════════════
    // 订阅数据构建
    // ══════════════════════════════════════════════

    /** 为某事件的所有根订阅者构建投递数据列表（执行条件判定为跳过时过滤）。 */
    protected <T extends IDomainEvent> List<SubscribeData> buildSubscribeDataList(T obj) {
        String eventName = this.resolveEventName(obj.getClass());
        Map<String, SubscriberInfo> subscriberMap = this.filterSubscriberInfoMap(eventName);
        return subscriberMap.entrySet().stream().map(entry -> {
            if (this.executeCheck(obj, entry.getValue().condition()) == ExecuteStatus.EXECUTE) {
                return this.createSubscribeData(obj,
                        entry.getKey(), eventName,
                        false, entry.getValue().deliveryPolicy());
            }
            return null;
        }).filter(Objects::nonNull).collect(Collectors.toList());
    }

    /** 为指定订阅者构建单条投递数据；订阅者不存在时返回 null。 */
    protected <T extends IDomainEvent> SubscribeData buildSubscribeData(final T obj,
                                                                        String subscriber,
                                                                        Boolean onlyThis) {
        String eventName = this.resolveEventName(obj.getClass());
        SubscriberInfo subscriberInfo = this.findSubscriberInfo(obj, subscriber, eventName);
        if (subscriberInfo == null) {
            return null;
        }
        return this.createSubscribeData(obj, subscriber, eventName, onlyThis, subscriberInfo.deliveryPolicy());
    }

    // ══════════════════════════════════════════════
    // 投递数据创建
    // ══════════════════════════════════════════════

    private <T extends IDomainEvent> SubscribeData createSubscribeData(final T obj,
                                                                       String subscriber,
                                                                       String eventName,
                                                                       Boolean onlyThis,
                                                                       DeliveryPolicy policy) {
        String jsonData = serializer.serialize(obj);
        return new SubscribeData(subscriber, jsonData, eventName, onlyThis, policy);
    }

    // ══════════════════════════════════════════════
    // 消费处理
    // ══════════════════════════════════════════════

    /**
     * 处理接收到的消息。
     * realEventName 由 createSubscribeData 始终写入，不再需要 mqTopic 回退。
     */
    @SuppressWarnings("unchecked")
    protected void handleEvent(String data, String mqTopic) {
        SubscribeData subscribeData = serializer.deserialize(data, SubscribeData.class);
        String event = subscribeData.getRealEventName();
        Map<String, SubscriberInfo> subscriberList = this.subscribers.get(event);
        if (subscriberList == null) {
            return;
        }
        AbstractEventSubscriber<?> subscriber =
                (AbstractEventSubscriber<?>) subscriberList.get(subscribeData.getName()).subscriber();
        if (subscriber != null) {
            // 用「管理器」serializer 一次性反序列化，类型来自订阅者声明
            Class<? extends IDomainEvent> eventType = subscriber.subscribedToEventType();
            IDomainEvent iDomainEvent = serializer.deserialize(subscribeData.getEventData(), eventType);
            // subscriber 注册时已绑定具体领域事件类型，此处做受控的未检查转换
            ((AbstractEventSubscriber<IDomainEvent>) subscriber).handleEvent(iDomainEvent);

            if (this.orderManager != null && !subscribeData.getOnlyThis()) {
                List<String> nextSubscribers = this.orderManager.findNextSubscribers(event, subscribeData.getName());
                // 复用已反序列化的 iDomainEvent，消除原 parseEvent 的二次反序列化
                nextSubscribers.forEach(s -> this.publish(iDomainEvent, s, subscribeData.getOnlyThis()));
            }
        }
    }
}
