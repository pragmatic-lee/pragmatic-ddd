package io.pragmatic.ddd.event.spi;

import io.pragmatic.ddd.event.IDomainEvent;

/**
 * 订阅者标记接口，声明其关心的领域事件类型。
 *
 * @author wizard-lee
 */
public interface ISubscriber {
    /** 返回该订阅者关注的领域事件类型。 */
    Class<? extends IDomainEvent> subscribedToEventType();
}
