package io.pragmatic.ddd.event.spi;

import io.pragmatic.ddd.event.IDomainEvent;

/**
 * 事件处理函数端口，承载订阅者的事件处理逻辑。
 *
 * @param <T> 领域事件类型
 * @author wizard-lee
 */
public interface IHandle<T extends IDomainEvent> {
    /** 处理一个领域事件。 */
    void handleEvent(T t);
}
