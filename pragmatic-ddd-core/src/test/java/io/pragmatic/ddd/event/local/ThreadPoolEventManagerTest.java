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
 * 验证本地线程池事件管理器的注册、发布与订阅者触发语义。
 *
 * @author wizard-lee
 */
class ThreadPoolEventManagerTest {

    private ThreadPoolEventManager manager;

    private static void logSubscriber(String alias, Object event) {
        String line = "============================================================";
        System.err.println(line);
        System.err.println(">>> [SUBSCRIBER TRIGGERED] alias=" + alias
                + " | thread=" + Thread.currentThread().getName());
        System.err.println(">>> event: " + event);
        System.err.println(line);
        System.err.flush();
    }

    @AfterEach
    void tearDown() {
        if (manager != null) {
            manager.shutdown();
        }
        System.err.flush();
    }

    @Test
    void registerAndPublish_triggersSubscriberHandle() throws InterruptedException {
        manager = new ThreadPoolEventManager(2, 4, 10, 3, 100, 1, new SubscriberOrderManager());

        CountDownLatch handled = new CountDownLatch(1);
        manager.registerSubscriber("sub-a", TestDomainEvent.class, e -> {
            logSubscriber("sub-a", e);
            handled.countDown();
        });

        manager.publish(new TestDomainEvent());

        assertThat(handled.await(5, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void publishToSpecificSubscriber_triggersOnlyTarget() throws InterruptedException {
        manager = new ThreadPoolEventManager(2, 4, 10, 3, 100, 1, new SubscriberOrderManager());

        AtomicBoolean aHandled = new AtomicBoolean(false);
        CountDownLatch bHandled = new CountDownLatch(1);
        manager.registerSubscriber("sub-a", TestDomainEvent.class, e -> {
            logSubscriber("sub-a", e);
            aHandled.set(true);
        });
        manager.registerSubscriber("sub-b", TestDomainEvent.class, e -> {
            logSubscriber("sub-b", e);
            bHandled.countDown();
        });

        manager.publish(new TestDomainEvent(), "sub-b");

        assertThat(bHandled.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(aHandled).isFalse();
    }

    @Test
    void publish_propagatesToDependentSubscriber() throws InterruptedException {
        manager = new ThreadPoolEventManager(2, 4, 10, 3, 100, 1, new SubscriberOrderManager());

        CountDownLatch aHandled = new CountDownLatch(1);
        CountDownLatch bHandled = new CountDownLatch(1);
        manager.registerSubscriber("sub-a", TestDomainEvent.class, e -> {
            logSubscriber("sub-a", e);
            aHandled.countDown();
        });
        manager.registerSubscriber("sub-b", TestDomainEvent.class, e -> {
            logSubscriber("sub-b", e);
            bHandled.countDown();
        }, (io.pragmatic.ddd.event.spi.IExecuteCondition<TestDomainEvent>) null, "sub-a");

        manager.publish(new TestDomainEvent());

        assertThat(aHandled.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(bHandled.await(5, TimeUnit.SECONDS)).isTrue();
    }
}
