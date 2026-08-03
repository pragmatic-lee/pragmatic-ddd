package io.pragmatic.ddd.rocketmq;

import io.pragmatic.ddd.base.AbstractSubscriberKey;
import io.pragmatic.ddd.event.internal.defaults.ConfigurableTopicResolver;
import io.pragmatic.ddd.event.internal.defaults.SubscriberOrderManager;
import io.pragmatic.ddd.event.internal.defaults.SubscriberFactory;
import io.pragmatic.ddd.event.spi.ExecuteStatus;
import io.pragmatic.ddd.event.spi.IExecuteCondition;
import io.pragmatic.ddd.event.spi.ITopicResolver;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * @author lixiaojing10
 * @date 2021/12/24 10:08 下午
 */
public class RocketMqDomainEventOrderedManagerTest {

    private static final String NAME_SERVER = "localhost:9876";

    private RocketMqEventManager createManager(ITopicResolver topicResolver) {
        return RocketMqEventManager.builder()
                .config(new RocketMqConfig().setNameServer(NAME_SERVER))
                .topicResolver(topicResolver)
                .orderManager(new SubscriberOrderManager())
                .build();
    }

    /**
     * 随机执行，订阅执行不分先后顺序
     * r1 ,r2 ,r3 的执行顺序不定
     */
    @Test
    public void randomExecuteTest() throws InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(4);
        ITopicResolver topicResolver = ConfigurableTopicResolver.builder()
                .globalDefaultTopic("test_event")
                .build();
        RocketMqEventManager rocketMqDomainEventManager = createManager(topicResolver);

        rocketMqDomainEventManager.initTopics();
        rocketMqDomainEventManager.start();
        rocketMqDomainEventManager.registerSubscriber(MyDomainEventSubscriberKey.R1, MyDomainEvent.class, s -> {
            countDownLatch.countDown();
            System.out.println("r1");
        });
        rocketMqDomainEventManager.registerSubscriber(MyDomainEventSubscriberKey.R2, MyDomainEvent.class, s -> {
            countDownLatch.countDown();
            System.out.println("r2");
        });
        rocketMqDomainEventManager.registerSubscriber(MyDomainEventSubscriberKey.R3, MyDomainEvent.class, s -> {
            countDownLatch.countDown();
            System.out.println("r3");
        });
        rocketMqDomainEventManager.publish(MyDomainEvent.buildEvent("全部执行", "全部执行"));
        rocketMqDomainEventManager.publish(MyDomainEvent.buildEvent("指定执行r3", "指定执行r3"), "r3");

        Thread.sleep(30000);
        Assertions.assertEquals(0L, countDownLatch.getCount());
    }

    /**
     * 验证按顺序执行 r3->r2->r1
     */
    @Test
    public void orderExecuteTest1() throws InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(5);
        ITopicResolver topicResolver = ConfigurableTopicResolver.builder()
                .globalDefaultTopic("test_event")
                .build();
        RocketMqEventManager rocketMqDomainEventManager = createManager(topicResolver);

        rocketMqDomainEventManager.initTopics();
        rocketMqDomainEventManager.start();
        rocketMqDomainEventManager.registerSubscriber(MyDomainEventSubscriberKey.R1, MyDomainEvent.class, s -> {
            countDownLatch.countDown();
            System.out.println(1);
        }, null, MyDomainEventSubscriberKey.R2);
        rocketMqDomainEventManager.registerSubscriber(MyDomainEventSubscriberKey.R2, MyDomainEvent.class, s -> {
            countDownLatch.countDown();
            System.out.println(2);
        }, null, MyDomainEventSubscriberKey.R3);
        rocketMqDomainEventManager.registerSubscriber(MyDomainEventSubscriberKey.R3, MyDomainEvent.class, s -> {
            countDownLatch.countDown();
            System.out.println(3);
        });
        rocketMqDomainEventManager.publish(MyDomainEvent.buildEvent("执行全部事件订阅", "执行全部事件订阅"));
        rocketMqDomainEventManager.publish(MyDomainEvent.buildEvent("执行指定的事件订阅，不执行依赖当前订阅的订阅", "执行指定的事件订阅，不执行依赖当前订阅的订阅"), MyDomainEventSubscriberKey.R2, true);
        rocketMqDomainEventManager.publish(MyDomainEvent.buildEvent("执行指定的事件订阅，同时执行依赖当前订阅的订阅", "执行指定的事件订阅，同时执行依赖当前订阅的订阅"), MyDomainEventSubscriberKey.R2, false);

        countDownLatch.await(30000, TimeUnit.SECONDS);
        Thread.sleep(30000);
        Assertions.assertEquals(0L, countDownLatch.getCount());
    }

    /**
     * 验证 r1、r3 依赖 r2
     */
    @Test
    public void orderExecuteTest2() throws InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(3);
        ITopicResolver topicResolver = ConfigurableTopicResolver.builder()
                .globalDefaultTopic("test_event")
                .build();
        RocketMqEventManager rocketMqDomainEventManager = createManager(topicResolver);

        rocketMqDomainEventManager.initTopics();
        rocketMqDomainEventManager.start();
        rocketMqDomainEventManager.registerSubscriber(ShareDomainEventSubscriberKey.R1, ShareDomainEvent.class, s -> {
            countDownLatch.countDown();
            System.out.println(s.getName() + "r1");
        }, null, ShareDomainEventSubscriberKey.R2);
        rocketMqDomainEventManager.registerSubscriber(ShareDomainEventSubscriberKey.R2, ShareDomainEvent.class, s -> {
            countDownLatch.countDown();
            System.out.println(s.getName() + "r2");
        });
        rocketMqDomainEventManager.registerSubscriber(ShareDomainEventSubscriberKey.R3, ShareDomainEvent.class, s -> {
            countDownLatch.countDown();
            System.out.println(s.getName() + "r3");
        }, null, ShareDomainEventSubscriberKey.R2);

        rocketMqDomainEventManager.publish(ShareDomainEvent.buildEvent("100", "share"));

        countDownLatch.await(30000, TimeUnit.SECONDS);
        Thread.sleep(30000);
        Assertions.assertEquals(0L, countDownLatch.getCount());
    }

    /**
     * 验证两个事件执行指定订阅
     */
    @Test
    public void towEventOrderExecute() throws InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(3);
        ITopicResolver topicResolver = ConfigurableTopicResolver.builder()
                .globalDefaultTopic("test_event")
                .build();
        RocketMqEventManager rocketMqDomainEventManager = createManager(topicResolver);

        rocketMqDomainEventManager.initTopics();
        rocketMqDomainEventManager.start();

        rocketMqDomainEventManager.registerSubscriber(MyDomainEventSubscriberKey.R1, MyDomainEvent.class, s -> {
            System.out.println("MyDomainEvent r1");
        });
        rocketMqDomainEventManager.registerSubscriber(MyDomainEventSubscriberKey.R2, MyDomainEvent.class, s -> {
            System.out.println("MyDomainEvent r2");
        }, null, MyDomainEventSubscriberKey.R1);
        rocketMqDomainEventManager.registerSubscriber(MyDomainEventSubscriberKey.R3, MyDomainEvent.class, s -> {
            System.out.println("MyDomainEvent r3");
        }, null, MyDomainEventSubscriberKey.R1);

        rocketMqDomainEventManager.registerSubscriber(ShareDomainEventSubscriberKey.R1, ShareDomainEvent.class, s -> {
            countDownLatch.countDown();
            System.out.println(s.getName() + "ShareDomainEvent r1");
        });
        rocketMqDomainEventManager.registerSubscriber(ShareDomainEventSubscriberKey.R2, ShareDomainEvent.class, s -> {
            countDownLatch.countDown();
            System.out.println(s.getName() + "ShareDomainEvent r2");
        });
        rocketMqDomainEventManager.registerSubscriber(ShareDomainEventSubscriberKey.R3, ShareDomainEvent.class, s -> {
            countDownLatch.countDown();
            System.out.println(s.getName() + "ShareDomainEvent r3");
        }, null, ShareDomainEventSubscriberKey.R1);

        rocketMqDomainEventManager.publish(ShareDomainEvent.buildEvent("100", "share"));
        rocketMqDomainEventManager.publish(MyDomainEvent.buildEvent("100", "100"));

        countDownLatch.await(30000, TimeUnit.SECONDS);
        Thread.sleep(30000);
        Assertions.assertEquals(0L, countDownLatch.getCount());
    }

    @Test
    public void orderExecuteWithConditionTest() throws InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(2);
        ITopicResolver topicResolver = ConfigurableTopicResolver.builder()
                .globalDefaultTopic("test_event")
                .build();
        RocketMqEventManager rocketMqDomainEventManager = createManager(topicResolver);

        rocketMqDomainEventManager.initTopics();
        rocketMqDomainEventManager.start();
        rocketMqDomainEventManager.registerSubscriber(MyDomainEventSubscriberKey.R1, MyDomainEvent.class, s -> {
            countDownLatch.countDown();
            System.out.println("r1");
        }, null, MyDomainEventSubscriberKey.R2);
        rocketMqDomainEventManager.registerSubscriber(MyDomainEventSubscriberKey.R2, MyDomainEvent.class, s -> {
            countDownLatch.countDown();
            System.out.println("2");
        }, evt -> evt.getName().equals("100") ? ExecuteStatus.EXECUTE : ExecuteStatus.SKIP);

        rocketMqDomainEventManager.publish(MyDomainEvent.buildEvent("100", "100"));
        rocketMqDomainEventManager.publish(MyDomainEvent.buildEvent("200", "200"));

        Thread.sleep(30000);
        Assertions.assertEquals(0L, countDownLatch.getCount());
    }

    /**
     * 测试重试执行，r2 依赖 r1
     */
    @Test
    public void retryOrderExecuteTest() throws InterruptedException {
        ITopicResolver topicResolver = ConfigurableTopicResolver.builder()
                .globalDefaultTopic("test_event")
                .build();
        RocketMqEventManager rocketMqDomainEventManager = createManager(topicResolver);

        rocketMqDomainEventManager.initTopics();
        rocketMqDomainEventManager.start();
        CountDownLatch countDownLatch = new CountDownLatch(2);
        rocketMqDomainEventManager.registerSubscriber(MyDomainEventSubscriberKey.R1, MyDomainEvent.class, s -> {
            if (countDownLatch.getCount() > 0) {
                countDownLatch.countDown();
                System.out.println(s.getName() + "run error " + countDownLatch.getCount());
                throw new RuntimeException("test exception");
            }
            System.out.println("run ok");
        });
        rocketMqDomainEventManager.registerSubscriber(MyDomainEventSubscriberKey.R2, MyDomainEvent.class, s -> {
            System.out.println("run ok r2");
        }, null, MyDomainEventSubscriberKey.R1);

        rocketMqDomainEventManager.publish(MyDomainEvent.buildEvent("100", "100"));

        countDownLatch.await(30000, TimeUnit.SECONDS);
        Thread.sleep(60000);
        Assertions.assertEquals(0L, countDownLatch.getCount());
    }
}

class MyDomainEventSubscriberKey extends AbstractSubscriberKey {

    public static final String R3 = "r3";
    public static final String R2 = "r2";
    public static final String R1 = "r1";

    protected void populateKeys() {
        this.getKeys().put(R3, buildKeySetting("r3的订阅"));
        this.getKeys().put(R2, buildKeySetting("r2的订阅"));
        this.getKeys().put(R1, buildKeySetting("r1的订阅"));
    }
}

class ShareDomainEventSubscriberKey extends AbstractSubscriberKey {

    public static final String R3 = "r3";
    public static final String R2 = "r2";
    public static final String R1 = "r1";

    @Override
    protected void populateKeys() {
        this.getKeys().put(R3, buildKeySetting("r3的订阅"));
        this.getKeys().put(R2, buildKeySetting("r2的订阅"));
        this.getKeys().put(R1, buildKeySetting("r1的订阅"));
    }
}
