package io.pragmatic.ddd.rocketmq;

import io.pragmatic.ddd.event.internal.defaults.ConfigurableTopicResolver;
import io.pragmatic.ddd.event.internal.defaults.SubscriberFactory;
import io.pragmatic.ddd.event.spi.ITopicResolver;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 事件消息环境隔离测试
 *
 * @author lixiaojing
 * @date 2021/3/25 5:12 下午
 */
public class MultiEnvironmentIsolationTest {

    private static final String NAME_SERVER = "localhost:9876";

    @Test
    public void emptyEnvironment() throws InterruptedException {
        this.build("", "send prod0 message");
    }

    @Test
    public void notEmptyEnvironment() throws InterruptedException {
        this.build("prod", "send prod1 message");
    }

    private void build(String environmentName, String message) throws InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(1);

        ITopicResolver topicResolver = ConfigurableTopicResolver.builder()
                .globalDefaultTopic("test_event")
                .build();

        RocketMqEventManager rocketMqDomainEventManager = RocketMqEventManager.builder()
                .config(new RocketMqConfig().setNameServer(NAME_SERVER))
                .environmentName(environmentName)
                .topicResolver(topicResolver)
                .build();

        rocketMqDomainEventManager.initTopics();
        rocketMqDomainEventManager.registerSubscriber("test1", MyDomainEvent.class, s -> {
            countDownLatch.countDown();
            System.out.println(s.name + " = " + message);
        });

        Thread.sleep(8000);
        System.out.println("send " + message);

        for (int x = 0; x < 100; x++) {
            rocketMqDomainEventManager.publish(new MyDomainEvent(message));
        }

        countDownLatch.await(30000, TimeUnit.SECONDS);
        Thread.sleep(30000);
        Assertions.assertEquals(0L, countDownLatch.getCount());
    }
}
