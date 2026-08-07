package io.pragmatic.ddd.rocketmq;

import io.pragmatic.ddd.event.spi.ExecuteStatus;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 4.x 订阅顺序执行 / 依赖链 / 条件 + 顺序 组合集成测试，需真实 RocketMQ。
 * 无 RocketMQ 环境时整类跳过。
 *
 * @author wizard-lee
 */
@Tag("integration")
@Tag("rocketmq-4x")
class RocketMqDomainEventOrderedManagerTest {

    private static final String DEFAULT_TOPIC = "pdd_ddd_default_topic";

    @BeforeAll
    static void available() {
        Assumptions.assumeTrue(RocketMqTestSupport.is4xAvailable(), "RocketMQ 4.x 不可用，跳过集成测试");
    }

    /**
     * 随机执行，订阅执行不分先后顺序。
     */
    @Test
    void randomExecuteTest() throws InterruptedException {
        RocketMqEventManager manager = RocketMqTestSupport.create4xManager(DEFAULT_TOPIC, RocketMqTestSupport.orderManager());
        CountDownLatch latch = new CountDownLatch(4);

        manager.initTopics();
        manager.start();
        manager.registerSubscriber(MyDomainEventKeys.R1, MyDomainEvent.class, s -> latch.countDown());
        manager.registerSubscriber(MyDomainEventKeys.R2, MyDomainEvent.class, s -> latch.countDown());
        manager.registerSubscriber(MyDomainEventKeys.R3, MyDomainEvent.class, s -> latch.countDown());

        manager.publish(MyDomainEvent.buildEvent("全部执行", "全部执行-" + System.nanoTime()));
        manager.publish(MyDomainEvent.buildEvent("指定执行r3", "指定执行r3"), "r3");

        assertThat(latch.await(30, TimeUnit.SECONDS)).isTrue();
    }

    /**
     * 验证按顺序执行 r3 -> r2 -> r1。
     */
    @Test
    void orderExecuteTest1() throws InterruptedException {
        RocketMqEventManager manager = RocketMqTestSupport.create4xManager(DEFAULT_TOPIC, RocketMqTestSupport.orderManager());
        CountDownLatch latch = new CountDownLatch(5);
        List<String> order = new java.util.concurrent.CopyOnWriteArrayList<>();

        manager.initTopics();
        manager.start();
        manager.registerSubscriber(MyDomainEventKeys.R1, MyDomainEvent.class, s -> {
            order.add("r1");
            latch.countDown();
        }, null, MyDomainEventKeys.R2);
        manager.registerSubscriber(MyDomainEventKeys.R2, MyDomainEvent.class, s -> {
            order.add("r2");
            latch.countDown();
        }, null, MyDomainEventKeys.R3);
        manager.registerSubscriber(MyDomainEventKeys.R3, MyDomainEvent.class, s -> {
            order.add("r3");
            latch.countDown();
        });

        manager.publish(MyDomainEvent.buildEvent("执行全部事件订阅", "执行全部事件订阅-" + System.nanoTime()));
        manager.publish(MyDomainEvent.buildEvent("执行指定的事件订阅，不执行依赖当前订阅的订阅", "1"), MyDomainEventKeys.R2, true);
        manager.publish(MyDomainEvent.buildEvent("执行指定的事件订阅，同时执行依赖当前订阅的订阅", "2"), MyDomainEventKeys.R2, false);

        assertThat(latch.await(30, TimeUnit.SECONDS)).isTrue();
        assertThat(order).containsExactly("r3", "r2", "r1");
    }

    /**
     * 验证 r1、r3 依赖 r2（并发依赖，仅验证均被触发）。
     */
    @Test
    void orderExecuteTest2() throws InterruptedException {
        RocketMqEventManager manager = RocketMqTestSupport.create4xManager(DEFAULT_TOPIC, RocketMqTestSupport.orderManager());
        CountDownLatch latch = new CountDownLatch(3);

        manager.initTopics();
        manager.start();
        manager.registerSubscriber(ShareDomainEventKeys.R1, ShareDomainEvent.class, s -> latch.countDown(), null, ShareDomainEventKeys.R2);
        manager.registerSubscriber(ShareDomainEventKeys.R2, ShareDomainEvent.class, s -> latch.countDown());
        manager.registerSubscriber(ShareDomainEventKeys.R3, ShareDomainEvent.class, s -> latch.countDown(), null, ShareDomainEventKeys.R2);

        manager.publish(ShareDomainEvent.buildEvent("100", "share-" + System.nanoTime()));

        assertThat(latch.await(30, TimeUnit.SECONDS)).isTrue();
    }

    /**
     * 验证两个事件各自执行指定订阅与依赖链。
     */
    @Test
    void towEventOrderExecute() throws InterruptedException {
        RocketMqEventManager manager = RocketMqTestSupport.create4xManager(DEFAULT_TOPIC, RocketMqTestSupport.orderManager());
        CountDownLatch latch = new CountDownLatch(3);

        manager.initTopics();
        manager.start();
        manager.registerSubscriber(MyDomainEventKeys.R1, MyDomainEvent.class, s -> { });
        manager.registerSubscriber(MyDomainEventKeys.R2, MyDomainEvent.class, s -> { }, null, MyDomainEventKeys.R1);
        manager.registerSubscriber(MyDomainEventKeys.R3, MyDomainEvent.class, s -> { }, null, MyDomainEventKeys.R1);
        manager.registerSubscriber(ShareDomainEventKeys.R1, ShareDomainEvent.class, s -> latch.countDown());
        manager.registerSubscriber(ShareDomainEventKeys.R2, ShareDomainEvent.class, s -> latch.countDown());
        manager.registerSubscriber(ShareDomainEventKeys.R3, ShareDomainEvent.class, s -> latch.countDown(), null, ShareDomainEventKeys.R1);

        manager.publish(ShareDomainEvent.buildEvent("100", "share-" + System.nanoTime()));
        manager.publish(MyDomainEvent.buildEvent("100", "100-" + System.nanoTime()));

        assertThat(latch.await(30, TimeUnit.SECONDS)).isTrue();
    }

    /**
     * 顺序 + 条件：r2 条件不满足时 SKIP，r1 不触发。
     */
    @Test
    void orderExecuteWithConditionTest() throws InterruptedException {
        RocketMqEventManager manager = RocketMqTestSupport.create4xManager(DEFAULT_TOPIC, RocketMqTestSupport.orderManager());
        CountDownLatch latch = new CountDownLatch(2);
        List<String> executed = new java.util.concurrent.CopyOnWriteArrayList<>();

        manager.initTopics();
        manager.start();
        manager.registerSubscriber(MyDomainEventKeys.R1, MyDomainEvent.class, s -> {
            executed.add("r1");
            latch.countDown();
        }, null, MyDomainEventKeys.R2);
        manager.registerSubscriber(MyDomainEventKeys.R2, MyDomainEvent.class, s -> {
            executed.add("r2");
            latch.countDown();
        }, evt -> evt.getName().equals("100") ? ExecuteStatus.EXECUTE : ExecuteStatus.SKIP);

        manager.publish(MyDomainEvent.buildEvent("100", "100-" + System.nanoTime()));
        manager.publish(MyDomainEvent.buildEvent("200", "200-" + System.nanoTime()));

        assertThat(latch.await(30, TimeUnit.SECONDS)).isTrue();
        assertThat(executed).containsExactlyInAnyOrder("r1", "r2");
    }

    /**
     * 顺序 + 重试：r2 依赖 r1，r1 失败时重试。
     */
    @Test
    void retryOrderExecuteTest() throws InterruptedException {
        RocketMqEventManager manager = RocketMqTestSupport.create4xManager(DEFAULT_TOPIC, RocketMqTestSupport.orderManager());
        CountDownLatch latch = new CountDownLatch(2);

        manager.initTopics();
        manager.start();
        manager.registerSubscriber(MyDomainEventKeys.R1, MyDomainEvent.class, s -> {
            if (latch.getCount() > 0) {
                latch.countDown();
                throw new RuntimeException("test exception");
            }
        });
        manager.registerSubscriber(MyDomainEventKeys.R2, MyDomainEvent.class, s -> { }, null, MyDomainEventKeys.R1);

        manager.publish(MyDomainEvent.buildEvent("100", "100-" + System.nanoTime()));

        assertThat(latch.await(60, TimeUnit.SECONDS)).isTrue();
    }
}
