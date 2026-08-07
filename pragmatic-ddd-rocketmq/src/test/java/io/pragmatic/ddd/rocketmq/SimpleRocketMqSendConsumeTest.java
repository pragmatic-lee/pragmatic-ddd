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

/**
 * 最基础的 RocketMQ 连通性验证用例：不依赖框架的领域事件管理器，
 * 直接使用原生 Producer / Consumer 验证本地 RocketMQ 是否能正常发送与消费。
 * <p>
 * 运行前需在本机部署好 RocketMQ 并启动 NameServer（默认 9876）与 Broker。
 *
 * @author wizard-lee
 */
@Tag("integration")
public class SimpleRocketMqSendConsumeTest {

    private static final String TOPIC = "pdd_ddd_smoke_topic";
    private static final String PRODUCER_GROUP = "simple_producer_group";
    private static final String CONSUMER_GROUP = "simple_consumer_group";

    @BeforeAll
    static void available() {
        Assumptions.assumeTrue(RocketMqTestSupport.is4xAvailable(), "RocketMQ 4.x 不可用，跳过连通性冒烟");
    }

    /**
     * 启动一个 Consumer 订阅主题，再启动 Producer 发送一条消息，
     * 验证消息能被成功消费，从而确认 RocketMQ 链路正常。
     */
    @Test
    public void sendAndConsume() throws InterruptedException, MQClientException, MQBrokerException, RemotingException {
        CountDownLatch latch = new CountDownLatch(1);
        String expectedBody = "hello-rocketmq-" + System.nanoTime();

        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(CONSUMER_GROUP);
        consumer.setNamesrvAddr(RocketMqTestSupport.nameServer());
        consumer.subscribe(TOPIC, "*");
        consumer.registerMessageListener(new MessageListenerConcurrently() {
            @Override
            public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> msgs,
                                                            ConsumeConcurrentlyContext context) {
                msgs.forEach(msg -> {
                    String body = new String(msg.getBody(), StandardCharsets.UTF_8);
                    System.out.println("消费到消息 topic=" + msg.getTopic() + " body=" + body);
                    if (expectedBody.equals(body)) {
                        latch.countDown();
                    }
                });
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            }
        });
        consumer.start();

        DefaultMQProducer producer = new DefaultMQProducer(PRODUCER_GROUP);
        producer.setNamesrvAddr(RocketMqTestSupport.nameServer());
        producer.start();

        Message message = new Message(TOPIC, expectedBody.getBytes(StandardCharsets.UTF_8));
        producer.send(message);
        System.out.println("已发送消息 body=" + expectedBody);

        boolean received = latch.await(10, TimeUnit.SECONDS);

        producer.shutdown();
        consumer.shutdown();

        if (!received) {
            throw new AssertionError("未在限定时间内消费到发送的消息，RocketMQ 链路异常");
        }
        System.out.println("RocketMQ 发送与消费验证通过");
    }
}
