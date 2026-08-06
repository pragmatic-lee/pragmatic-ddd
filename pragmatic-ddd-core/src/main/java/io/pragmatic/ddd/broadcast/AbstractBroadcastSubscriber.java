package io.pragmatic.ddd.broadcast;

import io.pragmatic.ddd.event.IDomainEvent;
import io.pragmatic.ddd.event.spi.IEventSerializer;
import io.pragmatic.ddd.event.spi.IHandle;

import java.util.Objects;

/**
 * 对外广播订阅者基类。
 * <p>
 * 当对外广播由领域事件订阅触发时，继承本类并订阅同一领域事件：收到事件后构建消息体、组装信封、
 * 经 IBroadcastMessenger 发往对接方约定的对外 topic。本类直接实现 IHandle，可被
 * IEventRegistry.registerSubscriber 直接注册。
 * <p>
 * 对外广播是框架独立的对外产物，与内部事件 MQ 链路无关；其 handleEvent 内部走 IBroadcastMessenger，
 * 而非事件 publish。
 *
 * @param <T> 领域事件类型
 * @param <P> 消息体类型
 * @author wizard-lee
 */
public abstract class AbstractBroadcastSubscriber<T extends IDomainEvent, P> implements IHandle<T> {

    private final IBroadcastMessenger messenger;
    private final IEventSerializer serializer;
    private final String broadcastTopic;
    private final String senderCode;

    protected AbstractBroadcastSubscriber(IBroadcastMessenger messenger,
                                           IEventSerializer serializer,
                                           String broadcastTopic,
                                           String senderCode) {
        this.messenger = Objects.requireNonNull(messenger, "IBroadcastMessenger required");
        this.serializer = Objects.requireNonNull(serializer, "IEventSerializer required");
        this.broadcastTopic = Objects.requireNonNull(broadcastTopic, "broadcastTopic required");
        this.senderCode = Objects.requireNonNull(senderCode, "senderCode required");
    }

    /** 订阅的事件类型，供注册时传入 Class&lt;T&gt;。 */
    public abstract Class<T> subscribedToEventType();

    /** 由领域事件构建对接方约定的消息体。 */
    protected abstract P buildPayload(T event);

    /** 用事件与消息体组装对外信封。 */
    protected abstract AggregateMessageEnvelope<P> wrap(T event, P payload);

    @Override
    public void handleEvent(T event) {
        P payload = this.buildPayload(event);
        AggregateMessageEnvelope<P> envelope = this.wrap(event, payload);
        String serialized = this.serializeEnvelope(envelope);
        this.messenger.send(this.broadcastTopic, this.senderCode, serialized);
    }

    private String serializeEnvelope(AggregateMessageEnvelope<P> envelope) {
        try {
            return this.serializer.serialize(envelope);
        } catch (RuntimeException e) {
            throw BroadcastExceptions.wrapEnvelope("serialize", e);
        }
    }
}
