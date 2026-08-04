package io.pragmatic.ddd.event.local;

import io.pragmatic.ddd.event.internal.defaults.SubscriberOrderManager;
import io.pragmatic.ddd.event.support.TestDomainEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证指定订阅者别名发布与 onlyThis 标志的语义。
 *
 * @author wizard-lee
 */
class ThreadPoolEventManagerUseKeyTest {

    private ThreadPoolEventManager manager;

    @AfterEach
    void tearDown() {
        if (manager != null) {
            manager.shutdown();
        }
    }

    @Test
    void publishToUnknownAlias_doesNotTriggerSubscriber() throws InterruptedException {
        manager = new ThreadPoolEventManager(4, 4, 10, 3, 100, 1, new SubscriberOrderManager());

        AtomicBoolean handled = new AtomicBoolean(false);
        manager.registerSubscriber("sub-a", TestDomainEvent.class, e -> handled.set(true));

        manager.publish(new TestDomainEvent(), "not-exist");

        Thread.sleep(500);
        assertThat(handled).isFalse();
    }

    @Test
    void publishWithOnlyThisFlag_doesNotPropagateToDependents() throws InterruptedException {
        manager = new ThreadPoolEventManager(4, 4, 10, 3, 100, 1, new SubscriberOrderManager());

        CountDownLatch aHandled = new CountDownLatch(1);
        AtomicBoolean bHandled = new AtomicBoolean(false);
        manager.registerSubscriber("sub-a", TestDomainEvent.class, e -> aHandled.countDown());
        manager.registerSubscriber("sub-b", TestDomainEvent.class, e -> bHandled.set(true),
                (io.pragmatic.ddd.event.spi.IExecuteCondition<TestDomainEvent>) null, "sub-a");

        manager.publish(new TestDomainEvent(), "sub-a", true);

        assertThat(aHandled.await(5, TimeUnit.SECONDS)).isTrue();
        Thread.sleep(500);
        assertThat(bHandled).isFalse();
    }

    @Test
    void publishToSpecificAlias_triggersOnlyTarget() throws InterruptedException {
        manager = new ThreadPoolEventManager(4, 4, 10, 3, 100, 1, new SubscriberOrderManager());

        AtomicBoolean aHandled = new AtomicBoolean(false);
        CountDownLatch bHandled = new CountDownLatch(1);
        manager.registerSubscriber("sub-a", TestDomainEvent.class, e -> aHandled.set(true));
        manager.registerSubscriber("sub-b", TestDomainEvent.class, e -> bHandled.countDown());

        manager.publish(new TestDomainEvent(), "sub-b");

        assertThat(bHandled.await(5, TimeUnit.SECONDS)).isTrue();
        Thread.sleep(300);
        assertThat(aHandled).isFalse();
    }
}
