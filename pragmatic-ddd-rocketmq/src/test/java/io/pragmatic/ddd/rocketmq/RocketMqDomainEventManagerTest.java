package io.pragmatic.ddd.rocketmq;

import io.pragmatic.ddd.event.internal.defaults.ConfigurableTopicResolver;
import io.pragmatic.ddd.event.internal.defaults.SubscriberOrderManager;
import io.pragmatic.ddd.event.internal.defaults.SubscriberFactory;
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
 * 在运行该单元测试前需要，本地部署好 rocketmq,并修改对应的nameServer地址
 * 测试基于rocketmq的领域事件发布订阅机制
 *
 * @author lixiaojing
 * @date 2021/3/17 5:22 下午
 */
public class RocketMqDomainEventManagerTest {

    private static final String NAME_SERVER = "localhost:9876";

    private RocketMqEventManager createManager( ITopicResolver topicResolver) {
        return RocketMqEventManager.builder()
                .config(new RocketMqConfig().setNameServer(NAME_SERVER)
                        .setConsumerGroup("RocketMqDomainEventManagerTest"))

                .topicResolver(topicResolver)
                .serializer(new Fastjson2EventSerializer())
                .build();
    }

    private RocketMqEventManager createManager(ITopicResolver topicResolver,
                                                SubscriberOrderManager pm) {
        return RocketMqEventManager.builder()
                .config(new RocketMqConfig().setNameServer(NAME_SERVER)
                        .setConsumerGroup("RocketMqDomainEventManagerTest"))
                .topicResolver(topicResolver)
                .orderManager(pm)
                .build();
    }

    /**
     * 使用类名做为Topic的发布订阅实现,输出abctest1和abctest2
     */
    @Test
    public void topicUseClassName() throws InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(2);
        ITopicResolver topicResolver = ConfigurableTopicResolver.builder()
                .globalDefaultTopic("test_event111")
                .build();
        RocketMqEventManager rocketMqDomainEventManager = createManager( topicResolver);

        rocketMqDomainEventManager.initTopics();
        rocketMqDomainEventManager.registerSubscriber("test1", MyDomainEvent.class, s -> {
            System.out.println(s.getName() + "test1");
            countDownLatch.countDown();
        });
        rocketMqDomainEventManager.registerSubscriber("test2", MyDomainEvent.class, s -> {
            System.out.println(s.getName() + "test2");
            countDownLatch.countDown();
        });
        rocketMqDomainEventManager.publish(MyDomainEvent.buildEvent("abc", "abc"));

        countDownLatch.await(30000, TimeUnit.SECONDS);
        Thread.sleep(30000);
        Assertions.assertEquals(0L, countDownLatch.getCount());
    }

    /**
     * 使用共享topic的发布订阅测试
     */
    @Test
    public void useShareTopicTest() throws InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(2);
        ITopicResolver topicResolver = ConfigurableTopicResolver.builder()
                .globalDefaultTopic("test_event")
                .build();
        RocketMqEventManager rocketMqDomainEventManager = createManager(topicResolver);

        rocketMqDomainEventManager.initTopics();
        rocketMqDomainEventManager.registerSubscriber("shareTest1", ShareDomainEvent.class, s -> {
            countDownLatch.countDown();
            System.out.println(s.getName() + "shareTest1");
        });
        rocketMqDomainEventManager.registerSubscriber("shareTest2", ShareDomainEvent.class, s -> {
            countDownLatch.countDown();
            System.out.println(s.getName() + "shareTest2");
        });
        rocketMqDomainEventManager.publish(ShareDomainEvent.buildEvent("100", "share"));

        countDownLatch.await(30000, TimeUnit.SECONDS);
        Thread.sleep(30000);
        Assertions.assertEquals(0L, countDownLatch.getCount());
    }

    /**
     * 带条件的订阅测试
     */
    @Test
    public void useConditionTest() throws InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(2);
        ITopicResolver topicResolver = ConfigurableTopicResolver.builder()
                .globalDefaultTopic("test_event")
                .build();
        RocketMqEventManager rocketMqDomainEventManager = createManager( topicResolver);

        rocketMqDomainEventManager.initTopics();
        rocketMqDomainEventManager.registerSubscriber("test1", ShareDomainEvent.class, s -> {
            System.out.println("test1");
            countDownLatch.countDown();
        }, evt -> evt.getName().equals("test1") ? ExecuteStatus.EXECUTE : ExecuteStatus.SKIP);
        rocketMqDomainEventManager.registerSubscriber("test2", ShareDomainEvent.class, s -> {
            System.out.println("test2");
            countDownLatch.countDown();
        }, evt -> evt.getName().equals("test2") ? ExecuteStatus.EXECUTE : ExecuteStatus.SKIP);

        rocketMqDomainEventManager.publish(ShareDomainEvent.buildEvent("100", "test1"));
        rocketMqDomainEventManager.publish(ShareDomainEvent.buildEvent("100", "test2"));

        countDownLatch.await(30000, TimeUnit.SECONDS);
        Thread.sleep(30000);
        Assertions.assertEquals(0L, countDownLatch.getCount());
    }

    /**
     * 延时订阅
     */
    @Test
    public void delayTest() throws InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(4);
        ITopicResolver topicResolver = ConfigurableTopicResolver.builder()
                .globalDefaultTopic("test_event")
                .build();
        RocketMqEventManager rocketMqDomainEventManager = createManager(topicResolver,
                new SubscriberOrderManager());

        rocketMqDomainEventManager.initTopics();
        rocketMqDomainEventManager.registerSubscriber("sub0", MyDomainEvent.class, s -> {
            countDownLatch.countDown();
            System.out.println(0);
        });
        rocketMqDomainEventManager.registerSubscriber("sub1", MyDomainEvent.class, s -> {
            countDownLatch.countDown();
            System.out.println(1);
        }, DELAYED);
        rocketMqDomainEventManager.registerSubscriber("sub2", MyDomainEvent.class, s -> {
            countDownLatch.countDown();
            System.out.println(2);
        }, DELAYED);
        rocketMqDomainEventManager.registerSubscriber("sub3", MyDomainEvent.class, s -> {
            countDownLatch.countDown();
            System.out.println(3);
        }, DELAYED);

        rocketMqDomainEventManager.publish(MyDomainEvent.buildEvent("100", "100"));

        countDownLatch.await(30000, TimeUnit.SECONDS);
        Thread.sleep(60000);
        Assertions.assertEquals(0L, countDownLatch.getCount());
    }

    /**
     * 测试重试执行
     */
    @Test
    public void retryTest() throws InterruptedException {
        ITopicResolver topicResolver = ConfigurableTopicResolver.builder()
                .globalDefaultTopic("test_event")
                .build();
        RocketMqEventManager rocketMqDomainEventManager = createManager(topicResolver,
                new SubscriberOrderManager());

        rocketMqDomainEventManager.initTopics();
        CountDownLatch countDownLatch = new CountDownLatch(3);
        rocketMqDomainEventManager.registerSubscriber("sub1", MyDomainEvent.class, s -> {
            if (countDownLatch.getCount() > 0) {
                countDownLatch.countDown();
                System.out.println(s.getName() + "run error " + countDownLatch.getCount());
                throw new RuntimeException("test exception");
            }
            System.out.println("run ok");
        });
        rocketMqDomainEventManager.registerSubscriber("sub2", MyDomainEvent.class, s -> {
            System.out.println("run ok sub2");
        });

        rocketMqDomainEventManager.publish(MyDomainEvent.buildEvent("100", "100"));

        countDownLatch.await(30000, TimeUnit.SECONDS);
        Thread.sleep(60000);
        Assertions.assertEquals(0L, countDownLatch.getCount());
    }
}
