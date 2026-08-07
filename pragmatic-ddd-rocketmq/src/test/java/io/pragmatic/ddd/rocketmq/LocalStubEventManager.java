package io.pragmatic.ddd.rocketmq;

import io.pragmatic.ddd.event.internal.manager.AbstractMQEventManager;
import io.pragmatic.ddd.event.internal.model.SubscribeData;
import io.pragmatic.ddd.event.spi.IEventSerializer;
import io.pragmatic.ddd.event.spi.ISubscriberOrderManager;
import io.pragmatic.ddd.event.spi.ITopicResolver;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 本地内存 Stub 事件管理器，继承 core 的 {@link AbstractMQEventManager}，
 * 不连接真实 RocketMQ：{@code sendMessage} 仅记录发送目标，便于纯单元测试
 * 验证「发布时构建了哪些 SubscribeData、发往哪些 topic、顺序链是否触发下游」。
 *
 * @author wizard-lee
 */
class LocalStubEventManager extends AbstractMQEventManager {

    /** 记录每次 sendMessage 的 (topic, subscribeData)。 */
    final List<SentRecord> sent = new ArrayList<>();

    LocalStubEventManager(ISubscriberOrderManager orderManager, IEventSerializer serializer, ITopicResolver topicResolver) {
        super(orderManager, serializer, topicResolver);
    }

    List<SentRecord> sent() {
        return sent;
    }

    @Override
    protected void initializeTopics(Set<String> topics) {
        // 不创建任何 topic
    }

    @Override
    protected <T extends io.pragmatic.ddd.event.IDomainEvent> void sendMessage(SubscribeData data, T event, String topic) {
        sent.add(new SentRecord(topic, data));
    }

    @Override
    public void shutdown() {
        // 无需关闭
    }

    /** 暴露受保护的消费入口，供测试模拟消费侧触发顺序链。 */
    void consume(String data, String mqTopic) {
        handleEvent(data, mqTopic);
    }

    /** 一次发送记录。 */
    record SentRecord(String topic, SubscribeData subscribeData) {
    }
}
