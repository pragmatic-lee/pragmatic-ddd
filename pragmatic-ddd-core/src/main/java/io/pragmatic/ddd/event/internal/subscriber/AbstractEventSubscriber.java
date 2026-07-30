package io.pragmatic.ddd.event.internal.subscriber;

import io.pragmatic.ddd.event.IDomainEvent;
import io.pragmatic.ddd.event.spi.IEventListener;

/**
 * 领域事件订阅者基类。
 * 消息体的反序列化由事件管理器在调用 handleEvent(T) 前完成。
 *
 * @author wizard-lee
 */
public abstract class AbstractEventSubscriber<T extends IDomainEvent> implements IEventListener<T> {

}
