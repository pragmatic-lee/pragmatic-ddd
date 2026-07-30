package io.pragmatic.ddd.event.spi;

import io.pragmatic.ddd.event.IDomainEvent;

/**
 * 领域事件订阅者接口，继承 {@link ISubscriber} 并声明事件处理逻辑。
 *
 * @param <T> 领域事件类型
 * @author wizard-lee
 */
public interface IEventListener<T extends IDomainEvent> extends ISubscriber {

    /** 处理一个领域事件。 */
    void handleEvent(T aDomainEvent);
}
