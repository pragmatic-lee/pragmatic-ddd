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
import org.apache.rocketmq.client.apis.consumer.MessageListener;
import org.apache.rocketmq.client.apis.consumer.PushConsumer;
import org.apache.rocketmq.client.apis.message.MessageView;
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
 *
 * @author wizard-lee
 */
public class RocketMqGrpcEventManager extends AbstractMQEventManager implements MessageListener {

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
    private final List<String> pendingConsumerTopics = new ArrayList<>();
    private final IEventMetrics metrics;

    // ── 生命周期状态 ──
    private volatile boolean started = false;

    // ══════════════════════════════════════════════
    // Builder
    // ══════════════════════════════════════════════

    /**
     * 创建 RocketMqGrpcEventManager 构建器。
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public ConsumeResult consume(MessageView messageView) {
        ByteBuffer bodyBuffer = messageView.getBody();
        byte[] bytes = new byte[bodyBuffer.remaining()];
        bodyBuffer.get(bytes);
        String data = new String(bytes, StandardCharsets.UTF_8);
        try {
            handleEvent(data, messageView.getTopic());
            log.debug("gRPC 消费成功 topic={} msgId={} attempt={}",
                    messageView.getTopic(), messageView.getMessageId(),
                    messageView.getDeliveryAttempt());
            metrics.recordConsume(messageView.getTopic(), "unknown", true,
                    messageView.getDeliveryAttempt() - 1);
            return ConsumeResult.SUCCESS;
        } catch (Exception e) {
            log.error("gRPC 消费异常 topic={} msgId={} attempt={}",
                    messageView.getTopic(), messageView.getMessageId(),
                    messageView.getDeliveryAttempt(), e);
            metrics.recordConsume(messageView.getTopic(), "unknown", false,
                    messageView.getDeliveryAttempt() - 1);
            if (messageView.getDeliveryAttempt() > config.getMaxReconsumeTimes()) {
                metrics.recordDlq(messageView.getTopic(), e.getClass().getSimpleName());
            }
            return ConsumeResult.FAILURE;
        }
    }

    /**
     * RocketMqGrpcEventManager 的构建器。
     */
    public static class Builder {
        private RocketMqConfig config;
        private ITopicResolver topicResolver;
        private Producer producer;
        private ISubscriberOrderManager orderManager;
        private IEventSerializer serializer;
        private IEventMetrics metrics;

        /**
         * 设置 RocketMQ 配置。
         */
        public Builder config(RocketMqConfig c) { this.config = c; return this; }
        /**
         * 设置主题解析器。
         */
        public Builder topicResolver(ITopicResolver r) { this.topicResolver = r; return this; }
        /**
         * 设置外部注入的 gRPC Producer（可选）。
         */
        public Builder producer(Producer p) { this.producer = p; return this; }
        /**
         * 设置订阅顺序管理器（可选）。
         */
        public Builder orderManager(ISubscriberOrderManager m) { this.orderManager = m; return this; }
        /**
         * 设置事件序列化器（可选）。
         */
        public Builder serializer(IEventSerializer s) { this.serializer = s; return this; }
        /**
         * 设置事件指标采集器（可选）。
         */
        public Builder metrics(IEventMetrics m) { this.metrics = m; return this; }

        /**
         * 构建并返回 RocketMqGrpcEventManager 实例。
         */
        public RocketMqGrpcEventManager build() {
            Objects.requireNonNull(config, "RocketMqConfig required");
            Objects.requireNonNull(topicResolver, "topicResolver required");
            return new RocketMqGrpcEventManager(this);
        }
    }

    private RocketMqGrpcEventManager(Builder b) {
        super(
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

    /**
     * 初始化各 Topic 对应的 gRPC 通道。
     * <p>
     * gRPC 的 Producer/Consumer 在 SDK 中 build 即连接，因此此处仅收集待消费的 topic，
     * 不创建任何客户端、不建立连接；真正的 build（即 start）由 {@link #start()} 受控触发。
     */
    @Override
    protected void initializeTopics(Set<String> topics) {
        // 仅收集 topic，延迟到 start() 才 build Producer/Consumer
        for (String topic : topics) {
            this.pendingConsumerTopics.add(topic);
            log.info("gRPC topic collected, topic={}", topic);
        }
    }

    // ══════════════════════════════════════════════
    // 发送
    // ══════════════════════════════════════════════

    /**
     * 发送领域事件消息到指定 Topic（gRPC）。
     */
    @Override
    protected <T extends IDomainEvent> void sendMessage(SubscribeData s, T obj, String topic) {
        long startNs = System.nanoTime();
        byte[] body = this.serializeSubscribeData(s);
        MessageBuilder builder = provider.newMessageBuilder()
                .setTopic(topic)
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
    private static long delayLevelToMillis(int level) {
        return DELAY_LEVEL_TO_MILLIS[Math.max(0, Math.min(level, DELAY_LEVEL_TO_MILLIS.length - 1))];
    }
    /**
     * 受控启动：在通道初始化完成后，真正 build Producer 与 Consumer，建立连接并开始收发。
     * <p>
     * gRPC 的 build 即连接/启动，因此此处才创建客户端；应用应待自身全部依赖就绪后再调用。
     */
    @Override
    public void start() {
        if (this.started) {
            log.warn("RocketMqGrpcEventManager already started, ignore");
            return;
        }
        // 1. Producer 受控 build（未注入时）
        if (this.producer == null) {
            try {
                ClientConfiguration clientConfig = ClientConfiguration.newBuilder()
                        .setEndpoints(config.getProxyAddr())
                        .enableSsl(false)
                        .build();
                this.producer = provider.newProducerBuilder()
                        .setClientConfiguration(clientConfig)
                        .setTopics(pendingConsumerTopics.toArray(new String[0]))
                        .build();
                log.info("gRPC Producer started, proxy={}", config.getProxyAddr());
            } catch (Exception e) {
                throw new RuntimeException("Failed to start gRPC Producer", e);
            }
        }
        // 2. Consumer 受控 build（即连接/启动）
        for (String topic : pendingConsumerTopics) {
            try {
                ClientConfiguration clientConfig = ClientConfiguration.newBuilder()
                        .setEndpoints(config.getProxyAddr())
                        .build();
                Map<String, FilterExpression> subscription = new HashMap<>();
                subscription.put(topic, FilterExpression.SUB_ALL);
                PushConsumer consumer = provider.newPushConsumerBuilder()
                        .setClientConfiguration(clientConfig)
                        .setConsumerGroup(config.getConsumerGroup())
                        .setSubscriptionExpressions(subscription)
                        .setMessageListener(this)
                        .build();
                this.consumerList.add(consumer);
                log.info("gRPC Consumer started, topic={}", topic);
            } catch (Exception e) {
                throw new RuntimeException("Failed to start gRPC Consumer for topic: " + topic, e);
            }
        }
        this.pendingConsumerTopics.clear();
        this.started = true;
        log.info("RocketMqGrpcEventManager started");
    }

    // ══════════════════════════════════════════════
    // 优雅关闭
    // ══════════════════════════════════════════════

    /**
     * 关闭管理器，释放 Producer 与 Consumer 资源。
     */
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
        this.started = false;
        log.info("RocketMqGrpcEventManager shutdown complete.");
    }
}
