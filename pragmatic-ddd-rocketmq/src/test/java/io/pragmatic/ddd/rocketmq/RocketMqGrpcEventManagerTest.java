package io.pragmatic.ddd.rocketmq;

import io.pragmatic.ddd.event.internal.defaults.ConfigurableTopicResolver;
import io.pragmatic.ddd.event.internal.defaults.SubscriberOrderManager;
import io.pragmatic.ddd.event.internal.model.DeliveryPolicy;
import io.pragmatic.ddd.event.spi.ExecuteStatus;
import io.pragmatic.ddd.event.spi.IExecuteCondition;
import io.pragmatic.ddd.event.spi.ITopicResolver;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static io.pragmatic.ddd.event.internal.model.DeliveryPolicy.DELAYED;

/**
 * RocketMQ 5.x gRPC 领域事件管理器的单元测试。
 * <p>
 * 运行前置：本地部署 RocketMQ 5.x 并开启 gRPC Proxy（默认 8081），
 * 将 {@link #PROXY_ADDR} 改为实际地址。验证发布/订阅主链路与关键行为。
 *
 * @author wizard-lee
 */
public class RocketMqGrpcEventManagerTest {

    private static final String PROXY_ADDR = "localhost:8081";

    private RocketMqGrpcEventManager createManager(ITopicResolver topicResolver) {
        return RocketMqGrpcEventManager.builder()
                .config(new RocketMqConfig()
                        .setProxyAddr(PROXY_ADDR)
                        .setConsumerGroup("RocketMqGrpcEventManagerTest"))
                .topicResolver(topicResolver)
                .serializer(new Fastjson2EventSerializer())
                .build();
    }

    private RocketMqGrpcEventManager createManager(ITopicResolver topicResolver,
                                                   SubscriberOrderManager orderManager) {
        return RocketMqGrpcEventManager.builder()
                .config(new RocketMqConfig()
                        .setProxyAddr(PROXY_ADDR)
                        .setConsumerGroup("RocketMqGrpcEventManagerTest"))
                .topicResolver(topicResolver)
                .orderManager(orderManager)
                .serializer(new Fastjson2EventSerializer())
                .build();
    }

    /**
     * 使用类名作为 Topic 的发布订阅实现，验证两个订阅者回调均被执行。
     */
    @Test
    public void topicUseClassName() throws InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(2);
        ITopicResolver topicResolver = ConfigurableTopicResolver.builder()
                .globalDefaultTopic("test_event111")
                .build();
        RocketMqGrpcEventManager manager = createManager(topicResolver);

        manager.initTopics();
        manager.registerSubscriber("test1", MyDomainEvent.class, s -> {
            System.out.println(s.getName() + "test1");
            countDownLatch.countDown();
        });
        manager.registerSubscriber("test2", MyDomainEvent.class, s -> {
            System.out.println(s.getName() + "test2");
            countDownLatch.countDown();
        });
        manager.publish(MyDomainEvent.buildEvent("abc", "abc"));

        try {
            countDownLatch.await(30, TimeUnit.SECONDS);
            Thread.sleep(30000);
            Assertions.assertEquals(0L, countDownLatch.getCount());
        } finally {
            manager.shutdown();
        }
    }

    /**
     * 使用共享 Topic 的发布订阅测试，验证两个订阅者回调均被执行。
     */
    @Test
    public void useShareTopicTest() throws InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(2);
        ITopicResolver topicResolver = ConfigurableTopicResolver.builder()
                .globalDefaultTopic("test_event111")
                .build();
        RocketMqGrpcEventManager manager = createManager(topicResolver);

        manager.initTopics();
        manager.registerSubscriber("shareTest1", ShareDomainEvent.class, s -> {
            countDownLatch.countDown();
            System.out.println(s.getName() + "shareTest1");
        });
        manager.registerSubscriber("shareTest2", ShareDomainEvent.class, s -> {
            countDownLatch.countDown();
            System.out.println(s.getName() + "shareTest2");
        });
        manager.publish(ShareDomainEvent.buildEvent("100", "share"));

        try {
            countDownLatch.await(30, TimeUnit.SECONDS);
            Thread.sleep(30000);
            Assertions.assertEquals(0L, countDownLatch.getCount());
        } finally {
            manager.shutdown();
        }
    }

    /**
     * 带条件的订阅测试，验证仅满足执行条件的订阅者被触发。
     */
    @Test
    public void useConditionTest() throws InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(2);
        ITopicResolver topicResolver = ConfigurableTopicResolver.builder()
                .globalDefaultTopic("test_event111")
                .build();
        RocketMqGrpcEventManager manager = createManager(topicResolver);

        manager.initTopics();
        IExecuteCondition<ShareDomainEvent> test1Condition = evt ->
                evt.getName().equals("test1") ? ExecuteStatus.EXECUTE : ExecuteStatus.SKIP;
        IExecuteCondition<ShareDomainEvent> test2Condition = evt ->
                evt.getName().equals("test2") ? ExecuteStatus.EXECUTE : ExecuteStatus.SKIP;
        manager.registerSubscriber("test1", ShareDomainEvent.class, s -> {
            System.out.println("test1");
            countDownLatch.countDown();
        }, test1Condition);
        manager.registerSubscriber("test2", ShareDomainEvent.class, s -> {
            System.out.println("test2");
            countDownLatch.countDown();
        }, test2Condition);

        manager.publish(ShareDomainEvent.buildEvent("100", "test1"));
        manager.publish(ShareDomainEvent.buildEvent("100", "test2"));

        try {
            countDownLatch.await(30, TimeUnit.SECONDS);
            Thread.sleep(30000);
            Assertions.assertEquals(0L, countDownLatch.getCount());
        } finally {
            manager.shutdown();
        }
    }

    /**
     * 延时投递测试，验证 DELAYED 策略订阅者最终收到消息。
     */
    @Test
    public void delayTest() throws InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(4);
        ITopicResolver topicResolver = ConfigurableTopicResolver.builder()
                .globalDefaultTopic("test_event111")
                .build();
        RocketMqGrpcEventManager manager = createManager(topicResolver, new SubscriberOrderManager());

        manager.initTopics();
        manager.registerSubscriber("sub0", MyDomainEvent.class, s -> {
            countDownLatch.countDown();
            System.out.println(0);
        });
        manager.registerSubscriber("sub1", MyDomainEvent.class, s -> {
            countDownLatch.countDown();
            System.out.println(1);
        }, DELAYED);
        manager.registerSubscriber("sub2", MyDomainEvent.class, s -> {
            countDownLatch.countDown();
            System.out.println(2);
        }, DELAYED);
        manager.registerSubscriber("sub3", MyDomainEvent.class, s -> {
            countDownLatch.countDown();
            System.out.println(3);
        }, DELAYED);

        manager.publish(MyDomainEvent.buildEvent("100", "100"));

        try {
            countDownLatch.await(30, TimeUnit.SECONDS);
            Thread.sleep(60000);
            Assertions.assertEquals(0L, countDownLatch.getCount());
        } finally {
            manager.shutdown();
        }
    }

    /**
     * 测试重试执行，验证订阅者首轮异常后由框架重试并最终成功。
     */
    @Test
    public void retryTest() throws InterruptedException {
        ITopicResolver topicResolver = ConfigurableTopicResolver.builder()
                .globalDefaultTopic("test_event111")
                .build();
        RocketMqGrpcEventManager manager = createManager(topicResolver, new SubscriberOrderManager());

        manager.initTopics();
        CountDownLatch countDownLatch = new CountDownLatch(3);
        manager.registerSubscriber("sub1", MyDomainEvent.class, s -> {
            if (countDownLatch.getCount() > 0) {
                countDownLatch.countDown();
                System.out.println(s.getName() + "run error " + countDownLatch.getCount());
                throw new RuntimeException("test exception");
            }
            System.out.println("run ok");
        });
        manager.registerSubscriber("sub2", MyDomainEvent.class, s -> {
            System.out.println("run ok sub2");
        });

        manager.publish(MyDomainEvent.buildEvent("100", "100"));

        try {
            countDownLatch.await(30, TimeUnit.SECONDS);
            Thread.sleep(60000);
            Assertions.assertEquals(0L, countDownLatch.getCount());
        } finally {
            manager.shutdown();
        }
    }
}
