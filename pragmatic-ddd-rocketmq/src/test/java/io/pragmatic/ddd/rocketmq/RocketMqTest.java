package io.pragmatic.ddd.rocketmq;

import org.apache.commons.lang3.RandomUtils;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;

import static java.lang.System.out;

/**
 * @author lixiaojing10
 * @date 2022/1/29 1:30 下午
 */
public class RocketMqTest {

    private final DefaultMQProducer defaultMQProducer1;
    private final DefaultMQProducer defaultMQProducer2;

    private DefaultMQPushConsumer consumer1;
    private DefaultMQPushConsumer consumer2;

    public RocketMqTest() {
        defaultMQProducer1 = new DefaultMQProducer("a");
        defaultMQProducer1.setNamesrvAddr("localhost:9876");

        defaultMQProducer2 = new DefaultMQProducer("b");
        defaultMQProducer2.setNamesrvAddr("localhost:9876");


    }

    @Test
    public void startConsumer1() throws MQClientException, InterruptedException {
        consumer1 = new DefaultMQPushConsumer("a");
        consumer1.setNamesrvAddr("localhost:9876");
        consumer1.subscribe("T1", "*");
        consumer1.subscribe("T3", "*");
        consumer1.registerMessageListener(new MessageListenerConcurrently() {
            @Override
            public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> msgs, ConsumeConcurrentlyContext context) {

                msgs.forEach(s -> {
                    String s1 = new String(s.getBody());
                    out.println(s.getTopic() + "-consumer1-" + s1);
                });

                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            }
        });
        consumer1.start();
        Thread.sleep(3000000);

    }

    @Test
    public void startConsumer2() throws MQClientException, InterruptedException {
        consumer2 = new DefaultMQPushConsumer("a");
        consumer2.setNamesrvAddr("localhost:9876");

        consumer2.subscribe("T1", "*");
        consumer2.subscribe("T3", "*");
        consumer2.registerMessageListener(new MessageListenerConcurrently() {
            @Override
            public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> msgs, ConsumeConcurrentlyContext context) {

                msgs.forEach(s -> {
                    String s1 = new String(s.getBody());
                    out.println(s.getTopic() + "-consumer2-" + s1);
                });

                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            }
        });

        consumer2.start();
        Thread.sleep(3000000);

    }


    @Test
    public void startProducer() throws MQClientException, InterruptedException {
        defaultMQProducer2.start();
        defaultMQProducer1.start();

        Stream.generate(() -> RandomUtils.nextInt(1, 2)).limit(10).forEach(s -> {

            try {
                //defaultMQProducer1.send(new Message("T1", ("p1" + s).getBytes(StandardCharsets.UTF_8)));
                //defaultMQProducer2.send(new Message("T1", ("p2" + s).getBytes(StandardCharsets.UTF_8)));
                defaultMQProducer2.send(new Message("T3", ("p3" + s).getBytes(StandardCharsets.UTF_8)));
            } catch (Exception ex) {
                out.println(ex);

            } finally {

            }
        });
        Thread.sleep(3000000);
    }
}
