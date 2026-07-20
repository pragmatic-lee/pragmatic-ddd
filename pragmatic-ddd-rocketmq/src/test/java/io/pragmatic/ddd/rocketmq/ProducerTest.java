package io.pragmatic.ddd.rocketmq;

import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.remoting.exception.RemotingException;
import org.junit.jupiter.api.Test;

import java.io.UnsupportedEncodingException;

/**
 * @author lixiaojing

 */
public class ProducerTest {

    @Test
    public void send() throws MQClientException, UnsupportedEncodingException, RemotingException, InterruptedException, MQBrokerException {

        DefaultMQProducer producer = new DefaultMQProducer("TTT");
        producer.setNamesrvAddr("localhost:9876");

        producer.start();

        Message message = new Message("TESTONE", "TESTONE".getBytes("UTF-8"));

        producer.send(message);
        producer.send(message);
        producer.send(message);
        producer.send(message);
        producer.send(message);

        Message message1 = new Message("TEST", "TEST".getBytes("UTF-8"));
        producer.send(message1);
        producer.send(message1);
        producer.send(message1);
        producer.send(message1);
        producer.send(message1);

        Thread.sleep(10000);
    }

    @Test
    public void consumer() throws MQClientException, InterruptedException {

        DefaultMQPushConsumer testone1 = this.create("TESTONE");
        Thread.sleep(1000);
        DefaultMQPushConsumer testone2 = this.create("TEST");

        Thread.sleep(900000);

    }

    private DefaultMQPushConsumer create(String topic) throws MQClientException {
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer("QQ");
        consumer.setInstanceName(String.valueOf(System.nanoTime()));
        consumer.setNamesrvAddr("localhost:9876");


        consumer.registerMessageListener((MessageListenerConcurrently) (list, consumeConcurrentlyContext) -> {
            for (Message msg : list) {
                System.out.println(new String(msg.getBody()) + "b");
            }

            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        });
        consumer.start();
        consumer.subscribe(topic, "*");

        return consumer;
    }


}
