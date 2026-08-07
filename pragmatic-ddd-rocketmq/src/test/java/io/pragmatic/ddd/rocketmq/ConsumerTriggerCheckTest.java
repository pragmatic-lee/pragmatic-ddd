package io.pragmatic.ddd.rocketmq;

import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.remoting.exception.RemotingException;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 纯原生消费者触发验证：不依赖框架 Manager，仅验证 broker 是否把消息
 * 投递到 consumer 的 listener（consumeMessage 是否被执行）。
 * <p>
 * 订阅框架测试同款 topic（pdd_ddd_classname_topic），topic 与框架 RocketMqEventManager
 * 的 initializeTopics 创建的 consumer 一致，可用于确认「消费那块儿」是否执行到。
 *
 * @author wizard-lee
 */
@Tag("integration")
public class ConsumerTriggerCheckTest {

    private static final String TOPIC = "pdd_ddd_classname_topic";
    private static final String PRODUCER_GROUP = "trigger_check_producer";
    private static final String CONSUMER_GROUP = "trigger_check_consumer";

    @BeforeAll
    static void available() {
        Assumptions.assumeTrue(RocketMqTestSupport.is4xAvailable(), "RocketMQ 4.x 不可用，跳过连通性冒烟");
    }

    /**
     * 发送一条消息并验证 consumeMessage listener 是否被触发。
     * 若 latch 在 10 秒内归零，说明 broker 已把消息投递到 listener；
     * 否则说明 consumer 未收到消息（订阅/group/tag 等环节有问题）。
     */
    @Test
    public void consumerListenerShouldBeTriggered() throws InterruptedException, MQClientException, MQBrokerException, RemotingException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> receivedBody = new AtomicReference<>();

        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(CONSUMER_GROUP);
        consumer.setNamesrvAddr(RocketMqTestSupport.nameServer());
        consumer.subscribe(TOPIC, "*");
        consumer.registerMessageListener(new MessageListenerConcurrently() {
            @Override
            public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> msgs,
                                                            ConsumeConcurrentlyContext context) {
                System.out.println(">>> consumeMessage 被触发, 消息条数=" + msgs.size());
                msgs.forEach(msg -> {
                    String body = new String(msg.getBody(), StandardCharsets.UTF_8);
                    receivedBody.set(body);
                    System.out.println(">>> 收到消息 topic=" + msg.getTopic()
                            + " tag=[" + msg.getTags() + "] body=" + body);
                    latch.countDown();
                });
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            }
        });
        consumer.start();
        System.out.println(">>> consumer 已启动, group=" + CONSUMER_GROUP + " topic=" + TOPIC);

        DefaultMQProducer producer = new DefaultMQProducer(PRODUCER_GROUP);
        producer.setNamesrvAddr(RocketMqTestSupport.nameServer());
        producer.start();
        String payload = "trigger-check-" + System.nanoTime();
        producer.send(new Message(TOPIC, payload.getBytes(StandardCharsets.UTF_8)));
        System.out.println(">>> 已发送消息 body=" + payload);

        boolean triggered = latch.await(10, TimeUnit.SECONDS);

        producer.shutdown();
        consumer.shutdown();

        if (triggered) {
            System.out.println(">>> 结论: 消费 listener 已执行到, body=" + receivedBody.get());
        } else {
            throw new AssertionError(">>> 结论: 消费 listener 未执行到（10s 内未收到消息）");
        }
    }
}
