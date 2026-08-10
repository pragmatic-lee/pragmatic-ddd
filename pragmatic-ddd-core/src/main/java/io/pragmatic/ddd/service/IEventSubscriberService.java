package io.pragmatic.ddd.service;

import io.pragmatic.ddd.event.IDomainEvent;
import io.pragmatic.ddd.event.spi.IHandle;

/**
 * 事件订阅领域服务基类接口（第一类）。
 * <p>契约继承本接口即声明其为领域事件订阅服务，事件总线按事件类型路由 {@code handleEvent}。
 *
 * @param <T> 订阅的领域事件类型
 * @author wizard-lee
 */
public interface IEventSubscriberService<T extends IDomainEvent>
        extends IDomainService, IHandle<T> {
}
