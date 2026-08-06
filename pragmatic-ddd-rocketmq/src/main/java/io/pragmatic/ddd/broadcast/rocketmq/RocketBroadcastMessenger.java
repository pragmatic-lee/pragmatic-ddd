package io.pragmatic.ddd.broadcast.rocketmq;

import io.pragmatic.ddd.broadcast.BroadcastExceptions;
import io.pragmatic.ddd.broadcast.IBroadcastMessenger;
import org.apache.rocketmq.client.producer.MQProducer;
import org.apache.rocketmq.common.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * 基于 RocketMQ Remoting 的对外广播发送实现。
 * <p>
 * 本身为无状态薄封装，持有应用层注入的（单例、已 start 的）MQProducer，
 * 不负责 Producer 的创建与生命周期；Producer 是否在广播与事件链路间共用，由应用层决定。
 *
 * @author wizard-lee
 */
public class RocketBroadcastMessenger implements IBroadcastMessenger {

    private static final Logger log = LoggerFactory.getLogger(RocketBroadcastMessenger.class);

    private final MQProducer producer;

    public RocketBroadcastMessenger(MQProducer producer) {
        this.producer = Objects.requireNonNull(producer, "MQProducer required (must be started by caller)");
    }

    @Override
    public void send(String topic, String senderCode, String serializedEnvelope) {
        Objects.requireNonNull(topic, "broadcast topic required");
        Objects.requireNonNull(serializedEnvelope, "serialized envelope required");
        byte[] body = serializedEnvelope.getBytes(StandardCharsets.UTF_8);
        // tags 留 null；keys=senderCode 便于对接方/运维按发送方编码排查
        Message msg = new Message(topic, null, senderCode, body);
        try {
            this.producer.send(msg);
            log.info("广播发送成功 topic={} senderCode={}", topic, senderCode);
        } catch (Exception e) {
            log.error("广播发送失败 topic={} senderCode={}", topic, senderCode, e);
            throw BroadcastExceptions.wrapSend(topic, e);
        }
    }
}
