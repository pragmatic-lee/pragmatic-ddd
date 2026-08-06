package io.pragmatic.ddd.broadcast;

/**
 * 对外广播消息发送端口，与框架内部事件 MQ 链路完全解耦。
 * <p>
 * 实现类负责把已序列化的信封发送到对接方约定的对外 topic；底层介质（RocketMQ / Kafka 等）
 * 由具体模块各自实现。topic 为对接方约定的字符串，不复用事件链路的 ITopicResolver。
 *
 * @author wizard-lee
 */
public interface IBroadcastMessenger {

    /**
     * 发送一条已序列化的对外广播信封。
     *
     * @param topic              对接方约定的对外 topic
     * @param senderCode         发送方订阅者编码，用于日志与追踪
     * @param serializedEnvelope 信封经 IEventSerializer 序列化后的字符串
     */
    void send(String topic, String senderCode, String serializedEnvelope);
}
