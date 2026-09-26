package io.pragmatic.ddd.kafka;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Kafka 回投器，承接延时 topic / 重试 topic 的到期消息并回投业务 topic。
 * <p>
 * 单实例单线程、单个 Consumer，消费组与主消费者一致（{@code group}），订阅全部
 * {@code {businessTopic}{topicSuffix}}（延时链路为 delay-topic-suffix，重试链路为 retry-topic-suffix）；
 * 分区头部消息未到期时 pause 分区并记录到期时间，到期后 resume 并逐条经共享 Producer 回投
 * {@code x-origin-topic}（原 key）；分区全部回投成功后提交该分区位移，回投失败不提交、不 pause，
 * 下轮重试，保证不丢消息。header 缺失或损坏时视为到期立即回投并告警；rebalance 回收分区前提交位移。
 * <p>
 * 两条链路各持一个实例，差异仅在订阅后缀与标识（{@code label}：delay / retry），用于线程名、
 * {@code client.id} 后缀与日志文案。
 *
 * @author wizard-lee
 */
final class KafkaRedeliverRelay {

    /** 回投消息头部：期望投递时刻（epoch millis，UTF-8 字符串编码）。 */
    static final String DELIVER_AT_HEADER = "x-deliver-at";

    /** 回投消息头部：回投目标业务 topic（UTF-8 编码）。 */
    static final String ORIGIN_TOPIC_HEADER = "x-origin-topic";

    /** 延时链路标识。 */
    static final String LABEL_DELAY = "delay";

    /** 重试链路标识。 */
    static final String LABEL_RETRY = "retry";

    private static final Logger log = LoggerFactory.getLogger(KafkaRedeliverRelay.class);

    private final Producer<String, byte[]> producer;

    private final KafkaProperties config;

    private final String label;

    private final String topicSuffix;

    private final Set<String> sourceTopics;

    private final AtomicBoolean running = new AtomicBoolean(false);

    private Consumer<String, byte[]> consumer;

    private Thread pollThread;

    /**
     * 构造回投器。
     *
     * @param producer       共享生产者（与主链路同一实例）
     * @param config         Kafka 配置
     * @param businessTopics 已初始化的业务 topic 集合
     * @param label          链路标识，delay / retry
     * @param topicSuffix    待订阅 topic 的后缀，{@code {businessTopic}{suffix}}
     */
    KafkaRedeliverRelay(Producer<String, byte[]> producer,
                        KafkaProperties config,
                        Set<String> businessTopics,
                        String label,
                        String topicSuffix) {
        this.producer = producer;
        this.config = config;
        this.label = label;
        this.topicSuffix = topicSuffix;
        this.sourceTopics = businessTopics.stream()
                .map(topic -> topic + topicSuffix)
                .collect(Collectors.toCollection(HashSet::new));
    }

    /**
     * 启动回投线程（单线程、单 Consumer）。
     */
    void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        this.consumer = buildConsumer();
        this.consumer.subscribe(sourceTopics, new ConsumerRebalanceListener() {
            @Override
            public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
                log.info(
                        "Kafka {} consumer 分配分区完成: group={}, partitions={}",
                        label,
                        config.group(),
                        partitions);
            }

            @Override
            public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
                try {
                    consumer.commitSync();
                } catch (Exception e) {
                    log.warn("{} consumer 回收分区前提交位移失败: group={}", label, config.group(), e);
                }
            }
        });
        this.pollThread = new Thread(this::pollLoop, "kafka-" + label + "-relay-" + config.group());
        this.pollThread.setDaemon(true);
        this.pollThread.start();
        log.info("Kafka {}回投器已启动: group={}, sourceTopics={}", description(), config.group(), sourceTopics);
    }

    /**
     * 停止回投：wakeup → join → close 三段式，close 严格在 poll 线程结束后执行。
     */
    void shutdown() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        if (consumer != null) {
            consumer.wakeup();
        }
        if (pollThread != null) {
            try {
                pollThread.join(10_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (consumer != null) {
            try {
                consumer.close();
            } catch (Exception e) {
                log.warn("{} consumer 关闭异常", label, e);
            }
            consumer = null;
        }
        pollThread = null;
        log.info("Kafka {}回投器已停止", description());
    }

    private void pollLoop() {
        Map<TopicPartition, Long> pausedDue = new HashMap<>();
        while (running.get()) {
            try {
                resumeDuePartitions(consumer, pausedDue);
                long timeoutMs = nextPollTimeout(pausedDue);
                ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofMillis(timeoutMs));
                for (TopicPartition tp : records.partitions()) {
                    processPartition(consumer, tp, records.records(tp), pausedDue);
                }
            } catch (WakeupException e) {
                break;
            } catch (Exception e) {
                log.error("Kafka {}回投循环异常", description(), e);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    /**
     * resume 所有已到期分区并清除对应 pause 记录。
     */
    void resumeDuePartitions(Consumer<String, byte[]> consumer, Map<TopicPartition, Long> pausedDue) {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<TopicPartition, Long>> iterator = pausedDue.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<TopicPartition, Long> entry = iterator.next();
            if (entry.getValue() <= now) {
                consumer.resume(List.of(entry.getKey()));
                iterator.remove();
            }
        }
    }

    /**
     * 计算本轮 poll 超时：取最近一次到期间隔与 pollTimeoutMs 的较小值，保证到期后第一时间 resume，同时不让 poll 空转过频。
     */
    long nextPollTimeout(Map<TopicPartition, Long> pausedDue) {
        long now = System.currentTimeMillis();
        long nearest = pausedDue.values().stream().min(Long::compare).orElse(Long.MAX_VALUE);
        if (nearest == Long.MAX_VALUE) {
            return config.pollTimeoutMs();
        }
        return Math.min(Math.max(nearest - now, 0), config.pollTimeoutMs());
    }

    /**
     * 处理单个分区的待回投消息：头部未到期则 pause 分区并记录到期时间；到期则逐条回投，
     * 分区全部回投成功后提交该分区位移；回投失败不提交、不 pause，下轮重试。
     */
    void processPartition(Consumer<String, byte[]> consumer,
                          TopicPartition tp,
                          List<ConsumerRecord<String, byte[]>> partRecords,
                          Map<TopicPartition, Long> pausedDue) {
        ConsumerRecord<String, byte[]> head = partRecords.get(0);
        Long headDeliverAt = readDeliverAt(head);
        if (headDeliverAt != null && headDeliverAt > System.currentTimeMillis()) {
            log.info("{}分区头部未到期，暂停分区: partition={}, deliverAt={}", description(), tp, headDeliverAt);
            // pause 只停止拉取、不回退拉取位置：必须先 seek 回头部位移，否则 resume 后该消息不会被重新返回
            consumer.seek(tp, head.offset());
            consumer.pause(List.of(tp));
            pausedDue.put(tp, headDeliverAt);
            return;
        }
        for (ConsumerRecord<String, byte[]> record : partRecords) {
            String originTopic = readOriginTopic(record);
            if (!redeliver(originTopic, record)) {
                log.warn(
                        "{}回投失败，保留消息下轮重试: partition={}, offset={}",
                        description(),
                        tp,
                        record.offset());
                return;
            }
        }
        long nextOffset = partRecords.get(partRecords.size() - 1).offset() + 1;
        try {
            consumer.commitSync(Map.of(tp, new OffsetAndMetadata(nextOffset)));
        } catch (Exception e) {
            log.warn("{}分区提交位移失败: partition={}", description(), tp, e);
        }
    }

    /**
     * 读取到期时间 header，缺失或损坏时返回 null（视为到期立即回投）并告警。
     */
    private Long readDeliverAt(ConsumerRecord<String, byte[]> record) {
        Header header = record.headers().lastHeader(DELIVER_AT_HEADER);
        if (header == null || header.value() == null) {
            log.warn(
                    "{}消息缺少 x-deliver-at header，视为到期立即回投: topic={}, offset={}",
                    description(),
                    record.topic(),
                    record.offset());
            return null;
        }
        try {
            return Long.parseLong(new String(header.value(), StandardCharsets.UTF_8));
        } catch (NumberFormatException e) {
            log.warn(
                    "{}消息 x-deliver-at header 损坏，视为到期立即回投: topic={}, offset={}",
                    description(),
                    record.topic(),
                    record.offset());
            return null;
        }
    }

    /**
     * 读取回投目标业务 topic，header 缺失时按源 topic 名去掉后缀反解（回退路径）并告警。
     */
    private String readOriginTopic(ConsumerRecord<String, byte[]> record) {
        Header header = record.headers().lastHeader(ORIGIN_TOPIC_HEADER);
        if (header != null && header.value() != null) {
            return new String(header.value(), StandardCharsets.UTF_8);
        }
        log.warn(
                "{}消息缺少 x-origin-topic header，按 topic 名反解回投目标: topic={}, offset={}",
                description(),
                record.topic(),
                record.offset());
        return record.topic().substring(0, record.topic().length() - topicSuffix.length());
    }

    /**
     * 经共享 Producer 回投一条消息到业务 topic（原 key、原消息体），有界等待。
     * header 除回投链路自用的 {@code x-deliver-at} / {@code x-origin-topic} 外全部继承：
     * 业务自定义 header 不丢，重试链路的 {@code x-reconsume}（累计失败次数）与
     * {@code x-retry-hop}（往返轮次）得以在回投后继续累加。
     *
     * @return 发送是否确认成功
     */
    private boolean redeliver(String originTopic, ConsumerRecord<String, byte[]> record) {
        ProducerRecord<String, byte[]> out = new ProducerRecord<>(
                originTopic,
                record.key(),
                record.value());
        for (Header header : record.headers()) {
            if (DELIVER_AT_HEADER.equals(header.key()) || ORIGIN_TOPIC_HEADER.equals(header.key())) {
                continue;
            }
            out.headers().add(header.key(), header.value());
        }
        try {
            producer.send(out).get(config.sendTimeoutMs(), TimeUnit.MILLISECONDS);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("{}回投被中断: topic={}", description(), originTopic, e);
            return false;
        } catch (Exception e) {
            log.error("{}回投失败: topic={}", description(), originTopic, e);
            return false;
        }
    }

    /**
     * 取日志用的中文链路标识。
     */
    private String description() {
        return switch (label) {
            case LABEL_DELAY -> "延时";
            case LABEL_RETRY -> "重试";
            default -> label;
        };
    }

    private Consumer<String, byte[]> buildConsumer() {
        Map<String, Object> props = new HashMap<>();
        props.put("bootstrap.servers", config.bootstrapServers());
        // 与主消费者共用同一 group.id；主/回投消费者订阅的 topic 集合不相交，
        // 分区分配按各自订阅隔离，回投分区只会落在回投消费者上
        props.put("group.id", config.group());
        props.put("enable.auto.commit", false);
        props.put("max.poll.records", config.maxPollRecords());
        props.put("auto.offset.reset", "earliest");
        props.put("key.deserializer", StringDeserializer.class);
        props.put("value.deserializer", ByteArrayDeserializer.class);
        if (!config.clientId().isEmpty()) {
            props.put("client.id", config.clientId() + "-" + label);
        }
        return new KafkaConsumer<>(props);
    }
}
