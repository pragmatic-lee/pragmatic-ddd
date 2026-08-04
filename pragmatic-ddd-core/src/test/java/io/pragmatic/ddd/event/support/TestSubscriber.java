package io.pragmatic.ddd.event.support;

import io.pragmatic.ddd.event.IDomainEvent;
import io.pragmatic.ddd.event.internal.subscriber.AbstractEventSubscriber;
import io.pragmatic.ddd.event.spi.ISubscriber;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 测试用订阅者固定装置，记录事件是否被处理，供事件管理器与订阅者相关测试使用。
 *
 * @author wizard-lee
 */
public class TestSubscriber extends AbstractEventSubscriber<TestDomainEvent> implements ISubscriber {

    private final AtomicBoolean handled;

    public TestSubscriber() {
        this.handled = new AtomicBoolean(false);
    }

    public TestSubscriber(AtomicBoolean handled) {
        this.handled = handled;
    }

    @Override
    public void handleEvent(TestDomainEvent event) {
        handled.set(true);
    }

    public boolean isHandled() {
        return handled.get();
    }

    @Override
    public Class<? extends IDomainEvent> subscribedToEventType() {
        return TestDomainEvent.class;
    }
}
