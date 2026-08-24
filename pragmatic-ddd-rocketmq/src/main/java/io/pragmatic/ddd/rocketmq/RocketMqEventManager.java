package io.pragmatic.ddd.rocketmq;

import com.google.common.base.Charsets;
import io.pragmatic.ddd.event.IDomainEvent;
import io.pragmatic.ddd.event.PublishEventException;
import io.pragmatic.ddd.event.RegisterDomainEventException;
import io.pragmatic.ddd.event.internal.defaults.NoOpEventMetrics;
import io.pragmatic.ddd.event.internal.defaults.SubscriberOrderManager;
import io.pragmatic.ddd.event.internal.manager.AbstractMQEventManager;
import io.pragmatic.ddd.event.internal.model.DeliveryPolicy;
import io.pragmatic.ddd.event.internal.model.SubscribeData;
import io.pragmatic.ddd.event.spi.IEventMetrics;
import io.pragmatic.ddd.event.spi.IEventSerializer;
import io.pragmatic.ddd.event.spi.ISubscriberOrderManager;
import io.pragmatic.ddd.event.spi.ITopicResolver;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.MQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.MQProducer;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * RocketMQ Remoting 领域事件管理器。
 * <p>
 * 基于 rocketmq-client 的 DefaultMQProducer / DefaultMQPushConsumer，
 * 兼容 RocketMQ 4.x / 5.x Broker。
 * <p>
 * 核心特性：
 * <ul>
 *   <li>通过 RocketMqConfig 统一配置入口</li>
 *   <li>Producer 支持外部注入（与 Spring 容器共享），未注入时框架自建；单实例复用</li>
 *   <li>Consumer 始终框架内部创建（不同 Consumer Group 完全独立，不可共用）</li>
 *   <li>消费异常返回 RECONSUME_LATER，保障最终一致性</li>
 *   <li>通过 IEventMetrics 支持可观测性</li>
 * </ul>
 *
 * @see io.pragmatic.ddd.event.spi.IEventManager
 * @author wizard-lee
 */
public class RocketMqEventManager extends AbstractMQEventManager
        implements MessageListenerConcurrently {

    private static final Logger log = LoggerFactory.getLogger(RocketMqEventManager.class);

    // ── 配置 ──
    private final RocketMqConfig config;

    // ── 资源 ──
    private volatile MQProducer sharedProducer;
    private boolean externalProducer;
    private final List<MQPushConsumer> consumerList = new ArrayList<>();

    // ── 生命周期状态 ──
    private volatile boolean started = false;

    // ── 可观测性 ──
    private final IEventMetrics metrics;

    // ══════════════════════════════════════════════
    // Builder
    // ══════════════════════════════════════════════

    /**
     * 创建 RocketMqEventManager 构建器。
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * RocketMqEventManager 的构建器。
     */
    public static class Builder {
        private RocketMqConfig config;
        private ITopicResolver topicResolver;
        private MQProducer producer;
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
         * 设置外部注入的 Producer（可选）。
         */
        public Builder producer(MQProducer p) { this.producer = p; return this; }
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
         * 构建并返回 RocketMqEventManager 实例。
         */
        public RocketMqEventManager build() {
            Objects.requireNonNull(config, "RocketMqConfig required");
            Objects.requireNonNull(topicResolver, "topicResolver required");
            return new RocketMqEventManager(this);
        }
    }

    private RocketMqEventManager(Builder b) {
        super(
                b.orderManager != null ? b.orderManager : new SubscriberOrderManager(),
                b.serializer != null ? b.serializer : new Fastjson2EventSerializer(),
                b.topicResolver);
        this.config = b.config;
        this.metrics = b.metrics != null ? b.metrics : new NoOpEventMetrics();
        if (b.producer != null) {
            this.sharedProducer = b.producer;
            this.externalProducer = true;
        }
    }

    // ══════════════════════════════════════════════
    // 生命周期：通道初始化
    // ══════════════════════════════════════════════

    /**
     * 初始化各 Topic 对应的 Producer 与 Consumer。
     * <p>
     * 仅创建连接对象、配置参数并注册监听，不发起任何网络连接或消息收发；
     * 真正的 start 由 {@link #start()} 受控触发，确保应用完全就绪后再收发。
     */
    @Override
    protected void initializeTopics(Set<String> topics) {
        // 1. Producer — 外部注入优先，未注入则创建对象（懒初始化，单实例复用），但先不 start
        if (this.sharedProducer == null) {
            synchronized (this) {
                if (this.sharedProducer == null) {
                    DefaultMQProducer p = new DefaultMQProducer(config.getProducerGroup());
                    p.setNamesrvAddr(config.getNameServer());
                    p.setRetryTimesWhenSendFailed(config.getRetryTimesWhenSendFailed());
                    p.setSendMsgTimeout(config.getSendMsgTimeout());
                    p.setCompressMsgBodyOverHowmuch(config.getCompressMsgBodyOverHowmuch());
                    this.sharedProducer = p;
                    log.info("Shared Producer created, group={}", config.getProducerGroup());
                }
            }
        }
        // 2. Consumer — 每 topic 独立实例，始终框架内部创建，先不 start
        for (String topic : topics) {
            try {
                DefaultMQPushConsumer c = new DefaultMQPushConsumer(config.getConsumerGroup());
                c.setNamesrvAddr(config.getNameServer());
                c.setMaxReconsumeTimes(config.getMaxReconsumeTimes());
                c.subscribe(topic, "*");
                c.registerMessageListener(this);
                this.consumerList.add(c);
                log.info("Consumer created, topic={}", topic);
            } catch (Exception ex) {
                throw new RegisterDomainEventException(topic, ex);
            }
        }
    }

    // ══════════════════════════════════════════════
    // 发送
    // ══════════════════════════════════════════════

    /**
     * 发送领域事件消息到指定 Topic。
     */
    @Override
    protected <T extends IDomainEvent> void sendMessage(SubscribeData s, T obj, String topic) {
        long startNs = System.nanoTime();
        byte[] body = this.serializeSubscribeData(s);
        Message msg = new Message(topic,null,obj.getEntityId(),body);
        if (s.getDeliveryPolicy() == DeliveryPolicy.DELAYED) {
            msg.setDelayTimeLevel(config.getDefaultDelayLevel());
        }
        try {
            this.sharedProducer.send(msg);
            long latencyMs = (System.nanoTime() - startNs) / 1_000_000;
            log.info("事件发送成功 topic={} eventType={} subscriber={} entityId={} latencyMs={}",
                    topic, s.getRealEventName(), s.getName(), obj.getEntityId(), latencyMs);
            metrics.recordPublish(topic, s.getRealEventName(), true, latencyMs);
        } catch (Exception e) {
            long latencyMs = (System.nanoTime() - startNs) / 1_000_000;
            log.error("事件发送失败 topic={} eventType={} subscriber={} entityId={} latencyMs={}",
                    topic, s.getRealEventName(), s.getName(), obj.getEntityId(), latencyMs, e);
            metrics.recordPublish(topic, s.getRealEventName(), false, latencyMs);
            throw new PublishEventException(obj.getEntityId(), e);
        }
    }
    /**
     * 并发消费消息，逐条派发领域事件。
     */
    @Override
    public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> msgList,
                                                     ConsumeConcurrentlyContext context) {
        if (msgList == null || msgList.isEmpty()) {
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        }
        boolean hasFailure = false;
        for (MessageExt msg : msgList) {
            try {
                String data = new String(msg.getBody(), StandardCharsets.UTF_8);
                this.handleEvent(data, msg.getTopic());
                log.debug("消费成功 topic={} msgId={}", msg.getTopic(), msg.getMsgId());
                metrics.recordConsume(msg.getTopic(), "unknown", true, msg.getReconsumeTimes());
            } catch (Exception e) {
                hasFailure = true;
                log.error("消费异常 topic={} msgId={} reconsumeTimes={}",
                        msg.getTopic(), msg.getMsgId(), msg.getReconsumeTimes(), e);
                metrics.recordConsume(msg.getTopic(), "unknown", false, msg.getReconsumeTimes());
                if (msg.getReconsumeTimes() >= config.getMaxReconsumeTimes()) {
                    handleDeadLetter(msg, e);
                    metrics.recordDlq(msg.getTopic(), e.getClass().getSimpleName());
                }
            }
        }
        return hasFailure
                ? ConsumeConcurrentlyStatus.RECONSUME_LATER
                : ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
    }
    private void handleDeadLetter(MessageExt msg, Exception cause) {
        String dlqTopic = msg.getTopic() + "%DLQ%";
        try {
            Message dlqMsg = new Message(dlqTopic, msg.getTags(), msg.getKeys(), msg.getBody());
            this.sharedProducer.send(dlqMsg);
            log.warn("消息进入死信队列 dlqTopic={} originalMsgId={} reconsumeTimes={} cause={}",
                    dlqTopic, msg.getMsgId(), msg.getReconsumeTimes(), cause.getMessage());
        } catch (Exception e) {
            log.error("投递死信队列失败 originalMsgId={}", msg.getMsgId(), e);
        }
    }

    /**
     * 受控启动：在通道初始化完成后，真正拉起 Producer 与 Consumer 的收发。
     * <p>
     * 应用应待自身全部依赖就绪后再调用本方法，避免 Consumer 提前拉消息而下游未准备好。
     */
    @Override
    public void start() {
        if (this.started) {
            log.warn("RocketMqEventManager already started, ignore");
            return;
        }
        // 确保消费通道就绪：依据 topicResolver 创建并订阅各 topic 的 Consumer
        // （此前由使用方手动调用，Spring 集成路径(initMethod="start")会遗漏，导致无消费者）
        initTopics();
        // 1. Producer 受控 start
        if (this.sharedProducer instanceof DefaultMQProducer p) {
            try {
                p.start();
                log.info("Shared Producer started, group={}", config.getProducerGroup());
            } catch (Exception e) {
                throw new RegisterDomainEventException(config.getProducerGroup(), e);
            }
        }
        // 2. Consumer 受控 start
        for (MQPushConsumer c : consumerList) {
            try {
                c.start();
                log.info("Consumer started");
            } catch (Exception ex) {
                throw new RegisterDomainEventException("consumer-start", ex);
            }
        }
        this.started = true;
        log.info("RocketMqEventManager started");
    }

    // ══════════════════════════════════════════════
    // 优雅关闭
    // ══════════════════════════════════════════════

    /**
     * 关闭管理器，释放 Producer 与 Consumer 资源。
     */
    @Override
    public void shutdown() {
        log.info("RocketMqEventManager shutting down...");
        for (MQPushConsumer c : consumerList) {
            try {
                c.shutdown();
            } catch (Exception e) {
                log.error("Consumer shutdown error", e);
            }
        }
        consumerList.clear();
        if (!externalProducer && this.sharedProducer != null) {
            try {
                this.sharedProducer.shutdown();
                log.info("Self-created Producer shutdown complete");
            } catch (Exception e) {
                log.error("Producer shutdown error", e);
            }
        }
        this.started = false;
        log.info("RocketMqEventManager shutdown complete.");
    }
}
