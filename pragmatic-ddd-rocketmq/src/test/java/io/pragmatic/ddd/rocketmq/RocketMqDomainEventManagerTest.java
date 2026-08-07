package io.pragmatic.ddd.rocketmq;

import io.pragmatic.ddd.event.internal.defaults.SubscriberOrderManager;
import io.pragmatic.ddd.event.internal.model.DeliveryPolicy;
import io.pragmatic.ddd.event.spi.ExecuteStatus;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static io.pragmatic.ddd.event.internal.model.DeliveryPolicy.DELAYED;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 4.x（Remoting 协议）领域事件发布订阅集成测试，需真实 RocketMQ（NameServer 已就绪、Topic 已预建）。
 * 无 RocketMQ 环境时整类跳过。
 *
 * @author wizard-lee
 */
@Tag("integration")
@Tag("rocketmq-4x")
class RocketMqDomainEventManagerTest {

    private static final String CLASSNAME_TOPIC = "pdd_ddd_classname_topic";
    private static final String DEFAULT_TOPIC = "pdd_ddd_default_topic";
    private static final String SINGLE_TOPIC = "pdd_ddd_single_topic";
    private static final String EVENT_A_TOPIC = "pdd_ddd_event_a_topic";
    private static final String EVENT_B_TOPIC = "pdd_ddd_event_b_topic";
    private static final String SUB_A_TOPIC = "pdd_ddd_sub_a_topic";

    @BeforeAll
    static void available() {
        Assumptions.assumeTrue(RocketMqTestSupport.is4xAvailable(), "RocketMQ 4.x 不可用，跳过集成测试");
    }

    /**
     * 使用类名作为 Topic 的发布订阅（模式：按事件分 Topic）。
     */
    @Test
    void topicUseClassName() throws InterruptedException {
        RocketMqEventManager manager = RocketMqTestSupport.create4xManager(CLASSNAME_TOPIC);
        CountDownLatch latch = new CountDownLatch(2);
        List<String> received = new java.util.concurrent.CopyOnWriteArrayList<>();

        manager.initTopics();
        manager.start();
        manager.registerSubscriber("test1", MyDomainEvent.class, s -> {
            received.add(s.getName());
            latch.countDown();
        });
        manager.registerSubscriber("test2", MyDomainEvent.class, s -> {
            received.add(s.getName());
            latch.countDown();
        });
        manager.publish(MyDomainEvent.buildEvent("abc-" + System.nanoTime(), "abc"));

        assertThat(latch.await(30, TimeUnit.SECONDS)).isTrue();
        assertThat(received).hasSize(2);
    }

    /**
     * 使用共享 topic 的发布订阅。
     */
    @Test
    void useShareTopicTest() throws InterruptedException {
        RocketMqEventManager manager = RocketMqTestSupport.create4xManager(DEFAULT_TOPIC);
        CountDownLatch latch = new CountDownLatch(2);

        manager.initTopics();
        manager.start();
        manager.registerSubscriber("shareTest1", ShareDomainEvent.class, s -> latch.countDown());
        manager.registerSubscriber("shareTest2", ShareDomainEvent.class, s -> latch.countDown());
        manager.publish(ShareDomainEvent.buildEvent("100", "share-" + System.nanoTime()));

        assertThat(latch.await(30, TimeUnit.SECONDS)).isTrue();
    }

    /**
     * 按条件激活：符合条件 EXECUTE、不符合 SKIP（被跳过订阅者不消费）。
     */
    @Test
    void useConditionTest() throws InterruptedException {
        RocketMqEventManager manager = RocketMqTestSupport.create4xManager(DEFAULT_TOPIC);
        CountDownLatch latch = new CountDownLatch(2);
        List<String> executed = new java.util.concurrent.CopyOnWriteArrayList<>();

        manager.initTopics();
        manager.start();
        manager.registerSubscriber("test1", ShareDomainEvent.class, s -> {
            executed.add(s.getName());
            latch.countDown();
        }, evt -> evt.getName().equals("test1") ? ExecuteStatus.EXECUTE : ExecuteStatus.SKIP);
        manager.registerSubscriber("test2", ShareDomainEvent.class, s -> {
            executed.add(s.getName());
            latch.countDown();
        }, evt -> evt.getName().equals("test2") ? ExecuteStatus.EXECUTE : ExecuteStatus.SKIP);

        manager.publish(ShareDomainEvent.buildEvent("100", "test1"));
        manager.publish(ShareDomainEvent.buildEvent("100", "test2"));

        assertThat(latch.await(30, TimeUnit.SECONDS)).isTrue();
        assertThat(executed).containsExactlyInAnyOrder("test1", "test2");
    }

    /**
     * 延时投递。
     */
    @Test
    void delayTest() throws InterruptedException {
        RocketMqEventManager manager = RocketMqTestSupport.create4xManager(DEFAULT_TOPIC, RocketMqTestSupport.orderManager());
        CountDownLatch latch = new CountDownLatch(4);

        manager.initTopics();
        manager.start();
        manager.registerSubscriber("sub0", MyDomainEvent.class, s -> latch.countDown());
        manager.registerSubscriber("sub1", MyDomainEvent.class, s -> latch.countDown(), DELAYED);
        manager.registerSubscriber("sub2", MyDomainEvent.class, s -> latch.countDown(), DELAYED);
        manager.registerSubscriber("sub3", MyDomainEvent.class, s -> latch.countDown(), DELAYED);

        manager.publish(MyDomainEvent.buildEvent("100", "100-" + System.nanoTime()));

        assertThat(latch.await(60, TimeUnit.SECONDS)).isTrue();
    }

    /**
     * 消费失败触发重试。
     */
    @Test
    void retryTest() throws InterruptedException {
        RocketMqEventManager manager = RocketMqTestSupport.create4xManager(DEFAULT_TOPIC, RocketMqTestSupport.orderManager());
        CountDownLatch latch = new CountDownLatch(3);

        manager.initTopics();
        manager.start();
        manager.registerSubscriber("sub1", MyDomainEvent.class, s -> {
            if (latch.getCount() > 0) {
                latch.countDown();
                throw new RuntimeException("test exception");
            }
        });
        manager.registerSubscriber("sub2", MyDomainEvent.class, s -> { });

        manager.publish(MyDomainEvent.buildEvent("100", "100-" + System.nanoTime()));

        assertThat(latch.await(60, TimeUnit.SECONDS)).isTrue();
    }

    /**
     * 订阅按顺序执行（r3 -> r2 -> r1），用序号记录验证触发顺序。
     */
    @Test
    void orderExecuteTest() throws InterruptedException {
        RocketMqEventManager manager = RocketMqTestSupport.create4xManager(DEFAULT_TOPIC, RocketMqTestSupport.orderManager());
        CountDownLatch latch = new CountDownLatch(3);
        List<String> order = new java.util.concurrent.CopyOnWriteArrayList<>();

        manager.initTopics();
        manager.start();
        manager.registerSubscriber("r1", MyDomainEvent.class, s -> {
            order.add("r1");
            latch.countDown();
        }, null, "r2");
        manager.registerSubscriber("r2", MyDomainEvent.class, s -> {
            order.add("r2");
            latch.countDown();
        }, null, "r3");
        manager.registerSubscriber("r3", MyDomainEvent.class, s -> {
            order.add("r3");
            latch.countDown();
        });

        manager.publish(MyDomainEvent.buildEvent("order-" + System.nanoTime(), "order"));
        assertThat(latch.await(30, TimeUnit.SECONDS)).isTrue();
        assertThat(order).containsExactly("r3", "r2", "r1");
    }

    /**
     * 模式 A：所有 Event 走同一个底层 Topic（单 Topic 全量）。
     */
    @Test
    void route_allEventsToSingleTopic() throws InterruptedException {
        RocketMqEventManager manager = RocketMqTestSupport.create4xManager(SINGLE_TOPIC);
        CountDownLatch latch = new CountDownLatch(2);
        List<String> received = new java.util.concurrent.CopyOnWriteArrayList<>();

        manager.initTopics();
        manager.start();
        manager.registerSubscriber("a", MyDomainEvent.class, s -> {
            received.add("MyDomainEvent:" + s.getName());
            latch.countDown();
        });
        manager.registerSubscriber("b", ShareDomainEvent.class, s -> {
            received.add("ShareDomainEvent:" + s.getName());
            latch.countDown();
        });

        manager.publish(MyDomainEvent.buildEvent("x-" + System.nanoTime(), "x"));
        manager.publish(ShareDomainEvent.buildEvent("100", "y-" + System.nanoTime()));

        assertThat(latch.await(30, TimeUnit.SECONDS)).isTrue();
        assertThat(received).hasSize(2);
    }

    /**
     * 模式 B：不同 Event 走不同 Topic（按 Event 分 Topic）。
     */
    @Test
    void route_differentEventsToDifferentTopics() throws InterruptedException {
        RocketMqEventManager manager = RocketMqEventManager.builder()
                .config(new RocketMqConfig().setNameServer(RocketMqTestSupport.nameServer())
                        .setConsumerGroup("pdd-ddd-test-4x"))
                .topicResolver(RocketMqTestSupport.perEventTypeResolver(
                        DEFAULT_TOPIC, MyDomainEvent.class.getName(), EVENT_A_TOPIC,
                        ShareDomainEvent.class.getName(), EVENT_B_TOPIC))
                .serializer(new Fastjson2EventSerializer())
                .build();
        CountDownLatch latch = new CountDownLatch(2);
        List<String> received = new java.util.concurrent.CopyOnWriteArrayList<>();

        manager.initTopics();
        manager.start();
        manager.registerSubscriber("a", MyDomainEvent.class, s -> {
            received.add("A:" + s.getName());
            latch.countDown();
        });
        manager.registerSubscriber("b", ShareDomainEvent.class, s -> {
            received.add("B:" + s.getName());
            latch.countDown();
        });

        manager.publish(MyDomainEvent.buildEvent("x", "x-" + System.nanoTime()));
        manager.publish(ShareDomainEvent.buildEvent("100", "y-" + System.nanoTime()));

        assertThat(latch.await(30, TimeUnit.SECONDS)).isTrue();
        assertThat(received).containsExactlyInAnyOrder("A:x", "B:y");
    }
}
