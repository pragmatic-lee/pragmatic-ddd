package io.pragmatic.ddd.rocketmq;

import io.pragmatic.ddd.event.IDomainEvent;
import io.pragmatic.ddd.event.PublishEventException;
import io.pragmatic.ddd.event.internal.defaults.NoOpEventMetrics;
import io.pragmatic.ddd.event.internal.defaults.SubscriberOrderManager;
import io.pragmatic.ddd.event.internal.manager.AbstractMQEventManager;
import io.pragmatic.ddd.event.internal.model.DeliveryPolicy;
import io.pragmatic.ddd.event.internal.model.SubscribeData;
import io.pragmatic.ddd.event.spi.IEventMetrics;
import io.pragmatic.ddd.event.spi.IEventSerializer;
import io.pragmatic.ddd.event.spi.ISubscriberOrderManager;
import io.pragmatic.ddd.event.spi.ITopicResolver;
import org.apache.rocketmq.client.apis.ClientConfiguration;
import org.apache.rocketmq.client.apis.ClientServiceProvider;
import org.apache.rocketmq.client.apis.consumer.ConsumeResult;
import org.apache.rocketmq.client.apis.consumer.FilterExpression;
import org.apache.rocketmq.client.apis.consumer.PushConsumer;
import org.apache.rocketmq.client.apis.producer.Producer;
import org.apache.rocketmq.client.apis.message.MessageBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * RocketMQ 5.x gRPC 领域事件管理器。
 * <p>
 * 基于 rocketmq-client-java 的 PushConsumer / Producer API。
 * 仅支持 RocketMQ 5.x Broker（需开启 gRPC Proxy），4.x Broker 不可用。
 * <p>
 * 延时消息：gRPC 使用 setDeliveryTimestamp 替代 setDelayTimeLevel；
 * 消费确认：MessageListener 返回 ConsumeResult.SUCCESS/FAILURE 替代 ConsumeConcurrentlyStatus；
 * Producer / Consumer 通过 ClientServiceProvider 工厂创建。
 */
public class RocketMqGrpcEventManager extends AbstractMQEventManager {

    private static final Logger log = LoggerFactory.getLogger(RocketMqGrpcEventManager.class);

    // RocketMQ 默认延时等级: 1s/5s/10s/30s/1m/2m/3m/4m/5m/6m/7m/8m/9m/10m/20m/30m/1h/2h
    private static final long[] DELAY_LEVEL_TO_MILLIS = {
            0, 1000, 5000, 10000, 30000, 60000, 120000, 180000,
            240000, 300000, 360000, 420000, 480000, 540000,
            600000, 1200000, 1800000, 3600000, 7200000
    };

    private final RocketMqConfig config;
    private final ClientServiceProvider provider;
    private Producer producer;
    private final List<PushConsumer> consumerList = new ArrayList<>();
    private final IEventMetrics metrics;

    // ══════════════════════════════════════════════
    // Builder
    // ══════════════════════════════════════════════

    /** @return new Builder */
    public static Builder builder() {
        return new Builder();
    }

    /** Builder for RocketMqGrpcEventManager. */
    public static class Builder {
        private RocketMqConfig config;
        private String environmentName;
        private ITopicResolver topicResolver;
        private Producer producer;
        private ISubscriberOrderManager orderManager;
        private IEventSerializer serializer;
        private IEventMetrics metrics;

        /** @param c RocketMQ configuration */
        public Builder config(RocketMqConfig c) { this.config = c; return this; }
        /** @param s environment name */
        public Builder environmentName(String s) { this.environmentName = s; return this; }
        /** @param r topic resolver */
        public Builder topicResolver(ITopicResolver r) { this.topicResolver = r; return this; }
        /** @param p gRPC producer (optional) */
        public Builder producer(Producer p) { this.producer = p; return this; }
        /** @param m subscriber order manager (optional) */
        public Builder orderManager(ISubscriberOrderManager m) { this.orderManager = m; return this; }
        /** @param s event serializer (optional) */
        public Builder serializer(IEventSerializer s) { this.serializer = s; return this; }
        /** @param m event metrics (optional) */
        public Builder metrics(IEventMetrics m) { this.metrics = m; return this; }

        /** @return new RocketMqGrpcEventManager instance */
        public RocketMqGrpcEventManager build() {
            Objects.requireNonNull(config, "RocketMqConfig required");
            Objects.requireNonNull(environmentName, "environmentName required");
            Objects.requireNonNull(topicResolver, "topicResolver required");
            return new RocketMqGrpcEventManager(this);
        }
    }

    private RocketMqGrpcEventManager(Builder b) {
        super(b.environmentName,
                b.orderManager != null ? b.orderManager : new SubscriberOrderManager(),
                b.serializer != null ? b.serializer : new Fastjson2EventSerializer(),
                b.topicResolver);
        this.config = b.config;
        this.metrics = b.metrics != null ? b.metrics : new NoOpEventMetrics();
        this.provider = ClientServiceProvider.loadService();
        if (b.producer != null) {
            this.producer = b.producer;
        }
    }

    // ══════════════════════════════════════════════
    // 通道初始化
    // ══════════════════════════════════════════════

    @Override
    protected void initializeTopics(Set<String> topics) {
        // 1. Producer — gRPC 客户端，注入优先，未注入则自建
        if (this.producer == null) {
            synchronized (this) {
                if (this.producer == null) {
                    try {
                        ClientConfiguration clientConfig = ClientConfiguration.newBuilder()
                                .setEndpoints(config.getProxyAddr())
                                .build();
                        this.producer = provider.newProducerBuilder()
                                .setClientConfiguration(clientConfig)
                                .setTopics(topics.toArray(new String[0]))
                                .build();
                        log.info("gRPC Producer created, proxy={}", config.getProxyAddr());
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to create gRPC Producer", e);
                    }
                }
            }
        }
        // 2. Consumer — gRPC PushConsumer，每 topic 独立实例
        for (String topic : topics) {
            try {
                ClientConfiguration clientConfig = ClientConfiguration.newBuilder()
                        .setEndpoints(config.getProxyAddr())
                        .build();
                Map<String, FilterExpression> subscription = new HashMap<>();
                subscription.put(topic, FilterExpression.SUB_ALL);
                PushConsumer consumer = provider.newPushConsumerBuilder()
                        .setClientConfiguration(clientConfig)
                        .setConsumerGroup(topic)
                        .setSubscriptionExpressions(subscription)
                        .setMessageListener(messageView -> {
                            ByteBuffer bodyBuffer = messageView.getBody();
                            byte[] bytes = new byte[bodyBuffer.remaining()];
                            bodyBuffer.get(bytes);
                            String data = new String(bytes, StandardCharsets.UTF_8);
                            try {
                                handleEvent(data, messageView.getTopic());
                                log.debug("gRPC 消费成功 topic={} msgId={} attempt={}",
                                        messageView.getTopic(), messageView.getMessageId(),
                                        messageView.getDeliveryAttempt());
                                metrics.recordConsume(topic, "unknown", true,
                                        messageView.getDeliveryAttempt() - 1);
                                return ConsumeResult.SUCCESS;
                            } catch (Exception e) {
                                log.error("gRPC 消费异常 topic={} msgId={} attempt={}",
                                        messageView.getTopic(), messageView.getMessageId(),
                                        messageView.getDeliveryAttempt(), e);
                                metrics.recordConsume(topic, "unknown", false,
                                        messageView.getDeliveryAttempt() - 1);
                                if (messageView.getDeliveryAttempt() > config.getMaxReconsumeTimes()) {
                                    metrics.recordDlq(topic, e.getClass().getSimpleName());
                                }
                                return ConsumeResult.FAILURE;
                            }
                        })
                        .build();
                this.consumerList.add(consumer);
                log.info("gRPC Consumer created and started, topic={}", topic);
            } catch (Exception e) {
                throw new RuntimeException("Failed to create gRPC Consumer for topic: " + topic, e);
            }
        }
    }

    // ══════════════════════════════════════════════
    // 发送
    // ══════════════════════════════════════════════

    @Override
    protected <T extends IDomainEvent> void sendMessage(SubscribeData s, T obj, String topic) {
        long startNs = System.nanoTime();
        byte[] body = this.serializeSubscribeData(s);
        MessageBuilder builder = provider.newMessageBuilder()
                .setTopic(topic)
                .setTag(this.environmentName)
                .setKeys(obj.getEntityId())
                .setBody(body);
        if (s.getDeliveryPolicy() == DeliveryPolicy.DELAYED) {
            builder.setDeliveryTimestamp(
                    System.currentTimeMillis() + delayLevelToMillis(config.getDefaultDelayLevel()));
        }
        try {
            this.producer.send(builder.build());
            long latencyMs = (System.nanoTime() - startNs) / 1_000_000;
            log.info("事件发送成功(gRPC) topic={} eventType={} subscriber={} entityId={} latencyMs={}",
                    topic, s.getRealEventName(), s.getName(), obj.getEntityId(), latencyMs);
            metrics.recordPublish(topic, s.getRealEventName(), true, latencyMs);
        } catch (Exception e) {
            long latencyMs = (System.nanoTime() - startNs) / 1_000_000;
            log.error("事件发送失败(gRPC) topic={} eventType={} subscriber={} entityId={} latencyMs={}",
                    topic, s.getRealEventName(), s.getName(), obj.getEntityId(), latencyMs, e);
            metrics.recordPublish(topic, s.getRealEventName(), false, latencyMs);
            throw new PublishEventException(obj.getEntityId(), e);
        }
    }

    // ══════════════════════════════════════════════
    // 延时等级转换
    // ══════════════════════════════════════════════

    private static long delayLevelToMillis(int level) {
        return DELAY_LEVEL_TO_MILLIS[Math.max(0, Math.min(level, DELAY_LEVEL_TO_MILLIS.length - 1))];
    }

    // ══════════════════════════════════════════════
    // 优雅关闭
    // ══════════════════════════════════════════════

    @Override
    public void shutdown() {
        log.info("RocketMqGrpcEventManager shutting down...");
        for (PushConsumer c : consumerList) {
            try {
                c.close();
            } catch (Exception e) {
                log.error("gRPC Consumer close error", e);
            }
        }
        consumerList.clear();
        if (this.producer != null) {
            try {
                this.producer.close();
                log.info("gRPC Producer closed");
            } catch (Exception e) {
                log.error("gRPC Producer close error", e);
            }
        }
        log.info("RocketMqGrpcEventManager shutdown complete.");
    }
}
