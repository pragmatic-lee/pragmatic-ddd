package io.pragmatic.ddd.event.internal.model;

import io.pragmatic.ddd.event.spi.ExecuteStatus;
import io.pragmatic.ddd.event.spi.IExecuteCondition;
import io.pragmatic.ddd.event.spi.ISubscriber;
import io.pragmatic.ddd.event.support.TestDomainEvent;
import io.pragmatic.ddd.event.support.TestSubscriber;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证事件订阅信息（订阅者、别名、执行条件、投递策略）的存取与延时判定。
 *
 * @author wizard-lee
 */
class SubscriberInfoTest {

    private final ISubscriber subscriber = new TestSubscriber();
    private final IExecuteCondition<TestDomainEvent> condition = t -> ExecuteStatus.EXECUTE;

    @Test
    void delayedPolicy_isDelayedTrue() {
        SubscriberInfo info = new SubscriberInfo(subscriber, "alias-a", condition, DeliveryPolicy.DELAYED);
        assertThat(info.subscriber()).isSameAs(subscriber);
        assertThat(info.alias()).isEqualTo("alias-a");
        assertThat(info.condition()).isSameAs(condition);
        assertThat(info.deliveryPolicy()).isEqualTo(DeliveryPolicy.DELAYED);
        assertThat(info.isDelayed()).isTrue();
    }

    @Test
    void immediatePolicy_isDelayedFalse() {
        SubscriberInfo info = new SubscriberInfo(subscriber, "alias-b", condition, DeliveryPolicy.IMMEDIATE);
        assertThat(info.isDelayed()).isFalse();
    }
}
