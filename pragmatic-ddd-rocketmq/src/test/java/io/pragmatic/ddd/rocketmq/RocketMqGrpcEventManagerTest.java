package io.pragmatic.ddd.rocketmq;

import io.pragmatic.ddd.event.internal.model.DeliveryPolicy;
import io.pragmatic.ddd.event.spi.ExecuteStatus;
import io.pragmatic.ddd.event.spi.IExecuteCondition;
import io.pragmatic.ddd.event.spi.ITopicResolver;
import org.junit.jupiter.api.AfterEach;
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
 * 5.x（gRPC Proxy 协议）领域事件管理器的集成测试，需真实 RocketMQ 5.x Proxy。
 * 无 RocketMQ 环境时整类跳过。
 *
 * @author wizard-lee
 */
@Tag("integration")
@Tag("rocketmq-5x")
class RocketMqGrpcEventManagerTest {

    private static final String CLASSNAME_TOPIC = "pdd_ddd_classname_topic";

    private RocketMqGrpcEventManager manager;

    @BeforeAll
    static void available() {
        Assumptions.assumeTrue(RocketMqTestSupport.is5xAvailable(), "RocketMQ 5.x 不可用，跳过集成测试");
    }

    @AfterEach
    void tearDown() {
        if (manager != null) {
            manager.shutdown();
        }
    }

    /**
     * 使用类名作为 Topic 的发布订阅实现，验证两个订阅者回调均被执行。
     */
    @Test
    void topicUseClassName() throws InterruptedException {
        manager = RocketMqTestSupport.create5xManager(CLASSNAME_TOPIC);
        CountDownLatch latch = new CountDownLatch(2);

        manager.start();
        manager.registerSubscriber("test1", MyDomainEvent.class, s -> latch.countDown());
        manager.registerSubscriber("test2", MyDomainEvent.class, s -> latch.countDown());
        manager.publish(MyDomainEvent.buildEvent("abc-" + System.nanoTime(), "abc"));

        assertThat(latch.await(30, TimeUnit.SECONDS)).isTrue();
    }

    /**
     * 使用共享 Topic 的发布订阅测试。
     */
    @Test
    void useShareTopicTest() throws InterruptedException {
        manager = RocketMqTestSupport.create5xManager(CLASSNAME_TOPIC);
        CountDownLatch latch = new CountDownLatch(2);

        manager.start();
        manager.registerSubscriber("shareTest1", ShareDomainEvent.class, s -> latch.countDown());
        manager.registerSubscriber("shareTest2", ShareDomainEvent.class, s -> latch.countDown());
        manager.publish(ShareDomainEvent.buildEvent("100", "share-" + System.nanoTime()));

        assertThat(latch.await(30, TimeUnit.SECONDS)).isTrue();
    }

    /**
     * 带条件的订阅测试，验证仅满足执行条件的订阅者被触发。
     */
    @Test
    void useConditionTest() throws InterruptedException {
        manager = RocketMqTestSupport.create5xManager(CLASSNAME_TOPIC);
        CountDownLatch latch = new CountDownLatch(2);
        List<String> executed = new java.util.concurrent.CopyOnWriteArrayList<>();

        manager.start();
        IExecuteCondition<ShareDomainEvent> test1Condition =
                evt -> evt.getName().equals("test1") ? ExecuteStatus.EXECUTE : ExecuteStatus.SKIP;
        IExecuteCondition<ShareDomainEvent> test2Condition =
                evt -> evt.getName().equals("test2") ? ExecuteStatus.EXECUTE : ExecuteStatus.SKIP;
        manager.registerSubscriber("test1", ShareDomainEvent.class, s -> {
            executed.add("test1");
            latch.countDown();
        }, test1Condition);
        manager.registerSubscriber("test2", ShareDomainEvent.class, s -> {
            executed.add("test2");
            latch.countDown();
        }, test2Condition);

        manager.publish(ShareDomainEvent.buildEvent("100", "test1"));
        manager.publish(ShareDomainEvent.buildEvent("100", "test2"));

        assertThat(latch.await(30, TimeUnit.SECONDS)).isTrue();
        assertThat(executed).containsExactlyInAnyOrder("test1", "test2");
    }

    /**
     * 延时投递测试，验证 DELAYED 策略订阅者最终收到消息。
     */
    @Test
    void delayTest() throws InterruptedException {
        manager = RocketMqTestSupport.create5xManager(CLASSNAME_TOPIC, RocketMqTestSupport.orderManager());
        CountDownLatch latch = new CountDownLatch(4);

        manager.start();
        manager.registerSubscriber("sub0", MyDomainEvent.class, s -> latch.countDown());
        manager.registerSubscriber("sub1", MyDomainEvent.class, s -> latch.countDown(), DELAYED);
        manager.registerSubscriber("sub2", MyDomainEvent.class, s -> latch.countDown(), DELAYED);
        manager.registerSubscriber("sub3", MyDomainEvent.class, s -> latch.countDown(), DELAYED);

        manager.publish(MyDomainEvent.buildEvent("100", "100-" + System.nanoTime()));

        assertThat(latch.await(60, TimeUnit.SECONDS)).isTrue();
    }

    /**
     * 测试重试执行，验证订阅者首轮异常后由框架重试并最终成功。
     */
    @Test
    void retryTest() throws InterruptedException {
        manager = RocketMqTestSupport.create5xManager(CLASSNAME_TOPIC, RocketMqTestSupport.orderManager());
        CountDownLatch latch = new CountDownLatch(3);

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
     * 订阅按顺序执行（r3 -> r2 -> r1），补齐 5.x 缺失的顺序执行用例。
     */
    @Test
    void orderExecuteTest() throws InterruptedException {
        manager = RocketMqTestSupport.create5xManager(CLASSNAME_TOPIC, RocketMqTestSupport.orderManager());
        CountDownLatch latch = new CountDownLatch(3);
        List<String> order = new java.util.concurrent.CopyOnWriteArrayList<>();

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
}
