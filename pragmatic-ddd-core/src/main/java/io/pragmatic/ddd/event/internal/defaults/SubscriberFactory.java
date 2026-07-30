package io.pragmatic.ddd.event.internal.defaults;

import io.pragmatic.ddd.event.IDomainEvent;
import io.pragmatic.ddd.event.internal.subscriber.AbstractEventSubscriber;
import io.pragmatic.ddd.event.spi.*;
import java.util.function.Function;

/**
 * 订阅者工厂，构建 ISubscriber 与 IExecuteCondition 实例。
 *
 * @author wizard-lee
 */
public class SubscriberFactory {

    protected SubscriberFactory() {
    }

    /** 构建一个订阅者实例，反序列化由管理器统一负责。 */
    public static <T extends IDomainEvent> ISubscriber build(Class<T> cls, IHandle<T> handle) {
        return new AbstractEventSubscriber<T>() {
            @Override
            public Class<T> subscribedToEventType() {
                return cls;
            }

            @Override
            public void handleEvent(T aDomainEvent) {
                handle.handleEvent(aDomainEvent);
            }
        };
    }

    public static <T extends IDomainEvent> IExecuteCondition<T> buildCondition(Class<T> cls,
                                                                               Function<T, ExecuteStatus> fn) {
        return fn::apply;
    }
}
