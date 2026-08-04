package io.pragmatic.ddd.event.internal.defaults;

import io.pragmatic.ddd.event.spi.ExecuteStatus;
import io.pragmatic.ddd.event.spi.IEventListener;
import io.pragmatic.ddd.event.spi.IExecuteCondition;
import io.pragmatic.ddd.event.spi.ISubscriber;
import io.pragmatic.ddd.event.support.TestDomainEvent;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证订阅者工厂构建 ISubscriber 与 IExecuteCondition 的行为。
 *
 * @author wizard-lee
 */
class SubscriberFactoryTest {

    @Test
    void build_returnsSubscriberWithEventTypeAndHandle() {
        AtomicBoolean handled = new AtomicBoolean(false);
        ISubscriber subscriber = SubscriberFactory.build(TestDomainEvent.class, e -> handled.set(true));
        assertThat(subscriber.subscribedToEventType()).isEqualTo(TestDomainEvent.class);

        IEventListener<TestDomainEvent> listener = cast(subscriber);
        TestDomainEvent event = new TestDomainEvent();
        listener.handleEvent(event);
        assertThat(handled).isTrue();
    }

    @Test
    void buildCondition_returnsConditionApplyingFunction() {
        IExecuteCondition<TestDomainEvent> condition =
                SubscriberFactory.buildCondition(TestDomainEvent.class, e -> ExecuteStatus.SKIP);
        assertThat(condition.status(new TestDomainEvent())).isEqualTo(ExecuteStatus.SKIP);
    }

    @Test
    void buildCondition_executesGivenFunction() {
        IExecuteCondition<TestDomainEvent> condition =
                SubscriberFactory.buildCondition(TestDomainEvent.class, e -> ExecuteStatus.EXECUTE);
        assertThat(condition.status(new TestDomainEvent())).isEqualTo(ExecuteStatus.EXECUTE);
    }

    @SuppressWarnings("unchecked")
    private static IEventListener<TestDomainEvent> cast(ISubscriber subscriber) {
        return (IEventListener<TestDomainEvent>) subscriber;
    }
}
