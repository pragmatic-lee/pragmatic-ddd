package io.pragmatic.ddd.kafka;

import io.pragmatic.ddd.event.EventException;
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
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.common.TopicPartition;
import com.alibaba.fastjson2.JSON;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Kafka 领域事件管理器，继承 {@link AbstractMQEventManager} 实现订阅（拉取消费）与发布。
 * <p>
 * 订阅侧按 {@link KafkaConfig#concurrency()} 创建多个独立的 {@link KafkaConsumer}（同 group.id），
 * 各自在独立后台线程中 poll，由 Kafka 自动分摊分区以实现单机多核并行；跨容器部署（同 group）可横向扩展。
 * 消费成功后整批手动提交 offset；消费失败且未达 {@code maxReconsume} 则不提交以触发重投，
 * 超过则转发至 {@code {topic}{dlqSuffix}} 死信 topic 后提交跳过。
 *
 * @author wizard-lee
 */
public class KafkaEventManager extends AbstractMQEventManager {

    private static final Logger log = LoggerFactory.getLogger(KafkaEventManager.class);

    private static final String RECONSUME_HEADER = "x-reconsume";

    private final KafkaConfig config;

    private final IEventMetrics metrics;

    private Producer<String, byte[]> sharedProducer;

    private boolean externalProducer;

    private final List<Consumer<String, byte[]>> consumerList = new ArrayList<>();

    private final List<Thread> pollThreads = new ArrayList<>();

    private final AtomicBoolean running = new AtomicBoolean(false);

    /** 重投计数：key = topic-partition-offset；rebalance/重启会重置（DLQ 消息头携带重试次数以持久化元数据）。 */
    private final ConcurrentHashMap<String, AtomicInteger> reconsumeCounter = new ConcurrentHashMap<>();

    KafkaEventManager(Builder builder) {
        super(
                builder.orderManager != null ? builder.orderManager : new SubscriberOrderManager(),
                builder.serializer != null ? builder.serializer : new KafkaEventSerializer(),
                builder.topicResolver);
        this.config = builder.config;
        this.metrics = builder.metrics != null ? builder.metrics : new NoOpEventMetrics();
        if (builder.producer != null) {
            this.sharedProducer = builder.producer;
            this.externalProducer = true;
        } else {
            this.externalProducer = false;
        }
    }

    /**
     * 构建 Kafka 事件管理器。
     *
     * @param config       Kafka 配置
     * @param topicResolver topic 解析器
     * @return 构建器
     */
    public static Builder builder(KafkaConfig config, ITopicResolver topicResolver) {
        return new Builder(config, topicResolver);
    }

    @Override
    protected void initializeTopics(Set<String> topics) {
        ensureProducer();
        for (int i = 0; i < config.concurrency(); i++) {
            Consumer<String, byte[]> consumer = buildConsumer();
            consumer.subscribe(topics, new ConsumerRebalanceListener() {
                @Override
                public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
                    log.info(
                            "Kafka consumer 分配分区完成: group={}, clientId={}, partitions={}",
                            config.group(),
                            consumerKey(consumer),
                            partitions);
                }

                @Override
                public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
                    log.info(
                            "Kafka consumer 回收分区: group={}, clientId={}, partitions={}",
                            config.group(),
                            consumerKey(consumer),
                            partitions);
                }
            });
            consumerList.add(consumer);
        }
    }

    /**
     * 取 consumer 的可读标识，优先用配置的 client.id，为空时回退到对象标识。
     *
     * @param consumer 目标 consumer
     * @return 用于日志打印的标识串
     */
    private String consumerKey(Consumer<String, byte[]> consumer) {
        return config.clientId().isEmpty()
                ? Integer.toHexString(System.identityHashCode(consumer))
                : config.clientId();
    }

    @Override
    public <T extends IDomainEvent> void sendMessage(SubscribeData s, T obj, String topic) throws EventException {
        long startNs = System.nanoTime();
        byte[] body = serializeSubscribeData(s);
        if (s.getDeliveryPolicy() == DeliveryPolicy.DELAYED) {
            log.warn("Kafka 不支持原生延时消息，DELAYED 策略降级为即时发送。topic={}", topic);
        }
        ProducerRecord<String, byte[]> record = new ProducerRecord<>(
                topic,
                obj.getEntityId(),
                body);
        try {
            sharedProducer.send(record).get();
            long latencyMs = (System.nanoTime() - startNs) / 1_000_000;
            metrics.recordPublish(topic, s.getRealEventName(), true, latencyMs);
        } catch (Exception e) {
            long latencyMs = (System.nanoTime() - startNs) / 1_000_000;
            metrics.recordPublish(topic, s.getRealEventName(), false, latencyMs);
            throw new PublishEventException("Kafka 发布事件失败: " + topic, e);
        }
    }

    @Override
    public void start() {
        if (running.compareAndSet(false, true)) {
            initTopics();
            startPollThreads();
        }
    }

    @Override
    public void shutdown() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        pollThreads.forEach(Thread::interrupt);
        pollThreads.forEach(thread -> {
            try {
                thread.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        pollThreads.clear();
        consumerList.forEach(Consumer::close);
        consumerList.clear();
        if (!externalProducer && sharedProducer != null) {
            sharedProducer.close();
            sharedProducer = null;
        }
        reconsumeCounter.clear();
    }

    private void startPollThreads() {
        for (Consumer<String, byte[]> consumer : consumerList) {
            Thread thread = new Thread(() -> pollLoop(consumer));
            thread.setName("kafka-event-consumer-" + config.group() + "-" + pollThreads.size());
            thread.setDaemon(true);
            thread.start();
            pollThreads.add(thread);
        }
    }

    private void pollLoop(Consumer<String, byte[]> consumer) {
        while (running.get()) {
            try {
                ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofMillis(config.pollTimeoutMs()));
                Map<TopicPartition, Long> seekBack = new HashMap<>();
                for (ConsumerRecord<String, byte[]> record : records) {
                    if (handleRecord(record) == HandleResult.RETRY) {
                        TopicPartition tp = new TopicPartition(record.topic(), record.partition());
                        seekBack.computeIfAbsent(tp, k -> record.offset());
                    }
                }
                if (seekBack.isEmpty()) {
                    consumer.commitSync();
                } else {
                    seekBack.forEach((tp, offset) -> {
                        log.warn("消费失败，回退位移重投 partition={} offset={}", tp, offset);
                        consumer.seek(tp, offset);
                    });
                }
            } catch (Exception e) {
                log.error("Kafka poll 循环异常", e);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    HandleResult handleRecord(ConsumerRecord<String, byte[]> record) {
        String key = record.topic() + "-" + record.partition() + "-" + record.offset();
        String data = new String(record.value(), StandardCharsets.UTF_8);
        try {
            handleEvent(data, record.topic());
            metrics.recordConsume(record.topic(), extractRealEventName(data), true, 0);
            reconsumeCounter.remove(key);
            return HandleResult.COMMIT;
        } catch (Exception e) {
            int times = reconsumeCounter.computeIfAbsent(key, k -> new AtomicInteger(readReconsumeHeader(record.headers())))
                    .incrementAndGet();
            metrics.recordConsume(record.topic(), extractRealEventName(data), false, times);
            if (times >= config.maxReconsume()) {
                sendToDlq(record, times);
                metrics.recordDlq(record.topic(), e.getClass().getSimpleName());
                reconsumeCounter.remove(key);
                return HandleResult.COMMIT;
            }
            return HandleResult.RETRY;
        }
    }

    /** 单条记录处理结果：COMMIT 表示可提交位点（成功或已转死信），RETRY 表示需重投（不提交位点）。 */
    enum HandleResult {
        COMMIT,
        RETRY
    }

    private void sendToDlq(ConsumerRecord<String, byte[]> record, int reconsumeTimes) {
        String dlqTopic = record.topic() + config.dlqSuffix();
        ProducerRecord<String, byte[]> dlqRecord = new ProducerRecord<>(
                dlqTopic,
                record.key(),
                record.value());
        for (Header header : record.headers()) {
            dlqRecord.headers().add(header.key(), header.value());
        }
        dlqRecord.headers().add(RECONSUME_HEADER, String.valueOf(reconsumeTimes).getBytes(StandardCharsets.UTF_8));
        try {
            sharedProducer.send(dlqRecord).get();
        } catch (Exception e) {
            log.error("转发死信消息失败: topic={}", dlqTopic, e);
        }
    }

    private int readReconsumeHeader(Headers headers) {
        Header header = headers.lastHeader(RECONSUME_HEADER);
        if (header == null) {
            return 0;
        }
        try {
            return Integer.parseInt(new String(header.value(), StandardCharsets.UTF_8));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String extractRealEventName(String data) {
        try {
            return JSON.parseObject(data).getString("realEventName");
        } catch (Exception e) {
            return "unknown";
        }
    }

    private void ensureProducer() {
        if (externalProducer || sharedProducer != null) {
            return;
        }
        Map<String, Object> props = new HashMap<>();
        props.put("bootstrap.servers", config.bootstrapServers());
        props.put("acks", config.ack());
        props.put("enable.idempotence", config.enableIdempotence());
        props.put("key.serializer", StringSerializer.class);
        props.put("value.serializer", ByteArraySerializer.class);
        if (!config.compressionType().isEmpty()) {
            props.put("compression.type", config.compressionType());
        }
        sharedProducer = new KafkaProducer<>(props);
    }

    protected Consumer<String, byte[]> buildConsumer() {
        Map<String, Object> props = new HashMap<>();
        props.put("bootstrap.servers", config.bootstrapServers());
        props.put("group.id", config.group());
        props.put("enable.auto.commit", false);
        props.put("max.poll.records", config.maxPollRecords());
        props.put("auto.offset.reset", config.autoOffsetReset());
        props.put("key.deserializer", StringDeserializer.class);
        props.put("value.deserializer", ByteArrayDeserializer.class);
        if (!config.clientId().isEmpty()) {
            props.put("client.id", config.clientId());
        }
        return new KafkaConsumer<>(props);
    }

    /**
     * Kafka 事件管理器构建器，对齐 RocketMQ 实现的构造方式。
     *
     * @author wizard-lee
     */
    public static final class Builder {

        private final KafkaConfig config;

        private final ITopicResolver topicResolver;

        private ISubscriberOrderManager orderManager;

        private IEventSerializer serializer = new KafkaEventSerializer();

        private IEventMetrics metrics = new NoOpEventMetrics();

        private Producer<String, byte[]> producer;

        private Builder(KafkaConfig config, ITopicResolver topicResolver) {
            this.config = config;
            this.topicResolver = topicResolver;
        }

        public Builder orderManager(ISubscriberOrderManager orderManager) {
            this.orderManager = orderManager;
            return this;
        }

        public Builder serializer(IEventSerializer serializer) {
            this.serializer = serializer;
            return this;
        }

        public Builder metrics(IEventMetrics metrics) {
            this.metrics = metrics;
            return this;
        }

        public Builder producer(Producer<String, byte[]> producer) {
            this.producer = producer;
            return this;
        }

        public KafkaEventManager build() {
            return new KafkaEventManager(this);
        }
    }
}
