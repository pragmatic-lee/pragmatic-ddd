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
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Kafka 领域事件管理器，继承 {@link AbstractMQEventManager} 实现订阅（拉取消费）与发布。
 * <p>
 * 订阅侧按 {@link KafkaConfig#concurrency()} 创建多个独立的 {@link KafkaConsumer}（同 group.id），
 * 各自在独立后台线程中 poll，由 Kafka 自动分摊分区以实现单机多核并行；跨容器部署（同 group）可横向扩展。
 * 可靠性语义（at-least-once，与 RocketMQ 实现一致）：
 * <ul>
 *     <li>消费成功则按分区提交 offset，重置该消息的重投计数；</li>
 *     <li>消费失败在本轮内联重试预算内立即重试（间隔取 {@code inlineRetryIntervalsMs}，默认 100ms / 500ms），
 *         预算耗尽且未达 {@code maxReconsume} 时转存 {@code {topic}{retryTopicSuffix}} 重试 topic，
 *         由 {@link KafkaRedeliverRelay} 按 {@code retryHopBackoffMs} 退避到期回投业务 topic；</li>
 *     <li>累计失败次数达到 {@code maxReconsume + 1}（即业务处理次数上限）时转发至
 *         {@code {topic}{dlqSuffix}} 死信 topic；重试 topic 与死信的转存失败均保持 RETRY 持续重试，不丢消息；</li>
 *     <li>正常失败不再阻塞同分区后续消息；批次超出 {@code batchBudgetMs} 时把同分区未处理部分 seek 回退，
 *         避免消费线程长时间不 poll 被判为失效成员；</li>
 *     <li>rebalance 回收分区前同步提交位移，减少重复消费；</li>
 *     <li>发布走有界等待（{@code sendTimeoutMs}），中断信号正确恢复；</li>
 *     <li>DELAYED 策略按 {@code delayedPolicy} 三档分发：immediate 降级 / reject 拒绝 / relay 延时
 *         topic 转存并由 {@link KafkaRedeliverRelay} 到期回投；</li>
 *     <li>停机走 wakeup → join → close 三段式，close 严格在 poll 线程结束后执行，内联等待分片检查停机标志。</li>
 * </ul>
 *
 * @author wizard-lee
 */
public class KafkaEventManager extends AbstractMQEventManager {

    private static final Logger log = LoggerFactory.getLogger(KafkaEventManager.class);

    private static final String RECONSUME_HEADER = "x-reconsume";

    private static final String RETRY_HOP_HEADER = "x-retry-hop";

    /** 内联重试等待的分片长度（毫秒），决定停机响应的上限延迟。 */
    private static final long BACKOFF_SLICE_MS = 100L;

    private final KafkaConfig config;

    private final IEventMetrics metrics;

    private Producer<String, byte[]> sharedProducer;

    private boolean externalProducer;

    private final List<Consumer<String, byte[]>> consumerList = new ArrayList<>();

    private final List<Thread> pollThreads = new ArrayList<>();

    /** 运行标志，由 start()/shutdown() 切换；包级可见以便测试直接驱动 pollLoop。 */
    final AtomicBoolean running = new AtomicBoolean(false);

    /** 重投计数：key = topic-partition-offset；初始值取消息 x-reconsume header（重投/重启后计数不归零）。 */
    private final ConcurrentHashMap<String, AtomicInteger> reconsumeCounter = new ConcurrentHashMap<>();

    /** 业务 topic 集合，由 {@link #initializeTopics(Set)} 累积，relay 模式下用于装配延时/重试回投器。 */
    private final Set<String> businessTopics = new HashSet<>();

    /** 延时回投器，仅 delayed-policy=relay 时创建并随 start() 拉起；包级可见以便测试断言装配结果。 */
    KafkaRedeliverRelay delayRelay;

    /** 重试回投器，仅 retry-topic-suffix 非空时创建并随 start() 拉起；包级可见以便测试断言装配结果。 */
    KafkaRedeliverRelay retryRelay;

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
        validateRetryConfig();
    }

    /**
     * 校验重试相关配置：表为空或非法、重试次数非正时告警一次，运行期按降级语义继续（不因配置问题中断链路）。
     */
    private void validateRetryConfig() {
        if (config.inlineRetryTimes() == 0) {
            log.warn(
                    "内联重试间隔表为空或非法，失败即转存（无内联重试）: kafka.inline-retry-intervals-ms={}",
                    config.inlineRetryIntervalsMs());
        }
        if (config.retryHopBackoffTable().length == 0 && !config.retryTopicSuffix().isEmpty()) {
            log.warn(
                    "重试退避表为空或非法，重试 topic 回投将无退避（立即回投）: kafka.retry-hop-backoff-ms={}",
                    config.retryHopBackoffMs());
        }
        if (config.maxReconsume() <= 0) {
            log.warn("最大重试次数非正，消息首次消费失败即转存: kafka.max-reconsume={}", config.maxReconsume());
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
        businessTopics.addAll(topics);
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
                    try {
                        consumer.commitSync();
                    } catch (Exception e) {
                        log.warn("回收分区前提交位移失败(可能已发生代际切换): group={}", config.group(), e);
                    }
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
        if (s.getDeliveryPolicy() != DeliveryPolicy.DELAYED) {
            publishNow(s, obj, topic);
            return;
        }
        switch (config.delayedPolicy()) {
            case "reject" -> throw new UnsupportedDeliveryException(
                    "Kafka 不支持原生延时消息，且 kafka.delayed-policy=reject，拒绝发送 DELAYED 事件: topic=" + topic);
            case "relay" -> sendToDelayTopic(s, obj, topic);
            default -> {
                log.warn("Kafka 不支持原生延时消息，kafka.delayed-policy=immediate 已降级为即时发送。topic={}", topic);
                publishNow(s, obj, topic);
            }
        }
    }

    /**
     * 即时发布一条事件消息到业务 topic，走有界等待与中断恢复契约。
     */
    private <T extends IDomainEvent> void publishNow(SubscribeData s, T obj, String topic) {
        long startNs = System.nanoTime();
        ProducerRecord<String, byte[]> record = new ProducerRecord<>(
                topic,
                obj.getEntityId(),
                serializeSubscribeData(s));
        try {
            sharedProducer.send(record).get(config.sendTimeoutMs(), TimeUnit.MILLISECONDS);
            metrics.recordPublish(topic, s.getRealEventName(), true, elapsedMs(startNs));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            metrics.recordPublish(topic, s.getRealEventName(), false, elapsedMs(startNs));
            throw new PublishEventException("Kafka 发布事件被中断: topic=" + topic, e);
        } catch (Exception e) {
            metrics.recordPublish(topic, s.getRealEventName(), false, elapsedMs(startNs));
            throw new PublishEventException("Kafka 发布事件失败: topic=" + topic, e);
        }
    }

    /**
     * relay 模式下将 DELAYED 事件转存到延时 topic：{businessTopic}{delayTopicSuffix}，
     * header 携带 x-deliver-at（发送时刻 + defaultDelaySeconds×1000）与 x-origin-topic；
     * 转存失败抛 PublishEventException 由 outbox 重推，不降级为即时发送。
     */
    private <T extends IDomainEvent> void sendToDelayTopic(SubscribeData s, T obj, String topic) {
        long startNs = System.nanoTime();
        long deliverAt = System.currentTimeMillis() + config.defaultDelaySeconds() * 1000L;
        ProducerRecord<String, byte[]> record = new ProducerRecord<>(
                topic + config.delayTopicSuffix(),
                obj.getEntityId(),
                serializeSubscribeData(s));
        record.headers().add(KafkaRedeliverRelay.DELIVER_AT_HEADER,
                Long.toString(deliverAt).getBytes(StandardCharsets.UTF_8));
        record.headers().add(KafkaRedeliverRelay.ORIGIN_TOPIC_HEADER, topic.getBytes(StandardCharsets.UTF_8));
        try {
            sharedProducer.send(record).get(config.sendTimeoutMs(), TimeUnit.MILLISECONDS);
            metrics.recordPublish(topic, s.getRealEventName(), true, elapsedMs(startNs));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            metrics.recordPublish(topic, s.getRealEventName(), false, elapsedMs(startNs));
            throw new PublishEventException("Kafka 延时事件转存被中断: topic=" + topic, e);
        } catch (Exception e) {
            metrics.recordPublish(topic, s.getRealEventName(), false, elapsedMs(startNs));
            throw new PublishEventException("Kafka 延时事件转存失败: topic=" + topic, e);
        }
    }

    private long elapsedMs(long startNs) {
        return (System.nanoTime() - startNs) / 1_000_000;
    }

    @Override
    public void start() {
        if (running.compareAndSet(false, true)) {
            initTopics();
            startRelays();
            startPollThreads();
        }
    }

    /**
     * 装配并拉起回投器：延时回投取决于 {@code delayed-policy=relay}，重试回投只取决于重试通道是否启用
     * （{@code retry-topic-suffix} 非空）——两者解耦，避免消息转存进一个无人回投的 topic。
     */
    private void startRelays() {
        if (businessTopics.isEmpty()) {
            return;
        }
        Set<String> topics = new HashSet<>(businessTopics);
        if ("relay".equals(config.delayedPolicy())) {
            this.delayRelay = new KafkaRedeliverRelay(
                    sharedProducer,
                    config,
                    topics,
                    KafkaRedeliverRelay.LABEL_DELAY,
                    config.delayTopicSuffix());
            this.delayRelay.start();
        }
        if (!config.retryTopicSuffix().isEmpty()) {
            this.retryRelay = new KafkaRedeliverRelay(
                    sharedProducer,
                    config,
                    topics,
                    KafkaRedeliverRelay.LABEL_RETRY,
                    config.retryTopicSuffix());
            this.retryRelay.start();
        }
    }

    @Override
    public void shutdown() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        consumerList.forEach(Consumer::wakeup);
        if (delayRelay != null) {
            delayRelay.shutdown();
        }
        if (retryRelay != null) {
            retryRelay.shutdown();
        }
        for (Thread thread : pollThreads) {
            try {
                thread.join(10_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
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

    /**
     * 消费循环：按分区处理本批记录，分区级隔离提交与回退。
     * 成功分区收集 OffsetAndMetadata(末条 offset+1) 逐分区提交；RETRY 记录即停止该分区并把该分区
     * seek 回该位置；本批处理超出 {@code batchBudgetMs} 时把同分区未处理部分 seek 回退、不提交，
     * 避免消费线程长时间不 poll 被判为失效成员；空批不提交不回退；WakeupException 用于优雅停机退出。
     * 包级可见，供测试直接驱动。
     */
    void pollLoop(Consumer<String, byte[]> consumer) {
        while (running.get()) {
            try {
                ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofMillis(config.pollTimeoutMs()));
                long batchDeadlineMs = System.currentTimeMillis() + config.batchBudgetMs();
                Map<TopicPartition, Long> seekBack = new HashMap<>();
                Map<TopicPartition, OffsetAndMetadata> toCommit = new HashMap<>();
                for (TopicPartition tp : records.partitions()) {
                    List<ConsumerRecord<String, byte[]>> partRecords = records.records(tp);
                    for (int i = 0; i < partRecords.size(); i++) {
                        if (handleRecord(partRecords.get(i)) == HandleResult.RETRY) {
                            seekBack.put(tp, partRecords.get(i).offset());
                            break;
                        }
                        if (System.currentTimeMillis() >= batchDeadlineMs && i + 1 < partRecords.size()) {
                            long resumeOffset = partRecords.get(i + 1).offset();
                            log.warn(
                                    "本批处理超出时间预算，剩余消息留待下轮: partition={} offset={}",
                                    tp,
                                    resumeOffset);
                            seekBack.put(tp, resumeOffset);
                            break;
                        }
                    }
                    if (!seekBack.containsKey(tp)) {
                        long nextOffset = partRecords.get(partRecords.size() - 1).offset() + 1;
                        toCommit.put(tp, new OffsetAndMetadata(nextOffset));
                    }
                }
                if (!toCommit.isEmpty()) {
                    consumer.commitSync(toCommit);
                }
                seekBack.forEach((tp, offset) -> {
                    log.warn("兜底回退位移，消息下轮重投 partition={} offset={}", tp, offset);
                    consumer.seek(tp, offset);
                });
            } catch (WakeupException e) {
                break;
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

    /**
     * 单条记录处理：在每轮内联重试预算内重复执行业务处理（间隔取内联重试间隔表），
     * 预算耗尽且未达最大消费尝试次数时转存重试 topic，达到上限则转死信；
     * 转存确认成功返回 COMMIT（调用方继续处理下一条），转存未确认 / 停机中断返回 RETRY（分区回退，不丢消息）。
     */
    HandleResult handleRecord(ConsumerRecord<String, byte[]> record) {
        String key = recordKey(record);
        String data = new String(record.value(), StandardCharsets.UTF_8);
        AtomicInteger counter = reconsumeCounter.computeIfAbsent(
                key,
                k -> new AtomicInteger(readReconsumeHeader(record.headers())));
        if (counter.get() >= config.attemptLimit()) {
            return forwardToDlq(record, key, counter.get(), null);
        }
        int remaining = config.attemptLimit() - counter.get();
        for (int attempt = 0; attempt < remaining; attempt++) {
            try {
                handleEvent(data, record.topic());
                metrics.recordConsume(record.topic(), extractRealEventName(data), true, 0);
                reconsumeCounter.remove(key);
                return HandleResult.COMMIT;
            } catch (Exception e) {
                int times = counter.incrementAndGet();
                metrics.recordConsume(record.topic(), extractRealEventName(data), false, times);
                log.warn(
                        "消息消费失败 topic={} partition={} offset={} times={}/{}",
                        record.topic(),
                        record.partition(),
                        record.offset(),
                        times,
                        config.attemptLimit(),
                        e);
                if (times >= config.attemptLimit()) {
                    return forwardToDlq(record, key, times, e);
                }
                if (times % config.roundAttempts() == 0) {
                    return forwardAfterInlineRetryExhausted(record, key, times, e);
                }
                if (!awaitRetryBackoff(times)) {
                    return HandleResult.RETRY;
                }
            }
        }
        return forwardToDlq(record, key, counter.get(), null);
    }

    /** 单条记录处理结果：COMMIT 表示可提交位点（成功或已转存），RETRY 表示需重投（不提交位点）。 */
    enum HandleResult {
        COMMIT,
        RETRY
    }

    /**
     * 内联重试预算耗尽后的分流出口：重试通道启用（retry-topic-suffix 非空）则转重试 topic，否则直接转死信。
     */
    private HandleResult forwardAfterInlineRetryExhausted(ConsumerRecord<String, byte[]> record,
                                                          String key,
                                                          int reconsumeTimes,
                                                          Exception cause) {
        if (config.retryTopicSuffix().isEmpty()) {
            return forwardToDlq(record, key, reconsumeTimes, cause);
        }
        return forwardToRetryTopic(record, key, reconsumeTimes);
    }

    /**
     * 转存消息到重试 topic：{topic}{retryTopicSuffix}，携带回投时刻、回投目标 topic、累计失败次数与往返轮次；
     * 转存确认成功返回 COMMIT（调用方继续处理下一条），未确认返回 RETRY（分区回退，下轮重投）。
     */
    private HandleResult forwardToRetryTopic(ConsumerRecord<String, byte[]> record,
                                             String key,
                                             int reconsumeTimes) {
        String retryTopic = record.topic() + config.retryTopicSuffix();
        int hop = nextHop(record.headers());
        long deliverAt = System.currentTimeMillis() + config.retryHopBackoffMs(hop);
        ProducerRecord<String, byte[]> retryRecord = new ProducerRecord<>(
                retryTopic,
                record.key(),
                record.value());
        copyHeaders(record, retryRecord);
        retryRecord.headers().add(RETRY_HOP_HEADER, String.valueOf(hop).getBytes(StandardCharsets.UTF_8));
        retryRecord.headers().add(RECONSUME_HEADER, String.valueOf(reconsumeTimes).getBytes(StandardCharsets.UTF_8));
        retryRecord.headers().add(
                KafkaRedeliverRelay.DELIVER_AT_HEADER,
                String.valueOf(deliverAt).getBytes(StandardCharsets.UTF_8));
        retryRecord.headers().add(
                KafkaRedeliverRelay.ORIGIN_TOPIC_HEADER,
                record.topic().getBytes(StandardCharsets.UTF_8));
        if (!sendWithTimeout(retryRecord)) {
            log.error(
                    "重试 topic 转存失败，保留消息下轮重投 topic={} partition={} offset={} times={}",
                    record.topic(),
                    record.partition(),
                    record.offset(),
                    reconsumeTimes);
            return HandleResult.RETRY;
        }
        reconsumeCounter.remove(key);
        log.warn(
                "消息转入重试 topic topic={} retryTopic={} offset={} times={} hop={} backoffMs={}",
                record.topic(),
                retryTopic,
                record.offset(),
                reconsumeTimes,
                hop,
                Math.max(deliverAt - System.currentTimeMillis(), 0));
        return HandleResult.COMMIT;
    }

    /**
     * 转发死信并决定提交语义：转发确认成功返回 COMMIT，未确认返回 RETRY（分区回退，不丢消息）。
     */
    private HandleResult forwardToDlq(ConsumerRecord<String, byte[]> record,
                                      String key,
                                      int reconsumeTimes,
                                      Exception cause) {
        if (!sendToDlq(record, reconsumeTimes)) {
            log.error(
                    "死信转发失败，保留消息下轮重投 topic={} partition={} offset={} times={}",
                    record.topic(),
                    record.partition(),
                    record.offset(),
                    reconsumeTimes);
            return HandleResult.RETRY;
        }
        metrics.recordDlq(record.topic(), reasonOf(cause));
        reconsumeCounter.remove(key);
        log.warn(
                "消息已转入死信队列 topic={} dlqTopic={} offset={} times={}",
                record.topic(),
                record.topic() + config.dlqSuffix(),
                record.offset(),
                reconsumeTimes);
        return HandleResult.COMMIT;
    }

    /**
     * 等待第 times 次失败后的内联重试间隔：分片 sleep，片间检查运行标志与中断，
     * 返回 false 表示应放弃本轮重试（调用方返回 RETRY，位移不提交）。
     */
    boolean awaitRetryBackoff(int times) {
        long remainingMs = config.inlineRetryBackoffMs(times);
        while (remainingMs > 0) {
            if (!running.get() || Thread.currentThread().isInterrupted()) {
                return false;
            }
            long sliceMs = Math.min(remainingMs, BACKOFF_SLICE_MS);
            try {
                Thread.sleep(sliceMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
            remainingMs -= sliceMs;
        }
        return true;
    }

    /**
     * 转发消息到死信 topic（继承原 header 并追加重投次数）。
     *
     * @return 转发是否确认成功；失败时调用方应保持 RETRY 不提交位移
     */
    private boolean sendToDlq(ConsumerRecord<String, byte[]> record, int reconsumeTimes) {
        ProducerRecord<String, byte[]> dlqRecord = new ProducerRecord<>(
                record.topic() + config.dlqSuffix(),
                record.key(),
                record.value());
        copyHeaders(record, dlqRecord);
        dlqRecord.headers().add(RECONSUME_HEADER, String.valueOf(reconsumeTimes).getBytes(StandardCharsets.UTF_8));
        return sendWithTimeout(dlqRecord);
    }

    /**
     * 经共享 Producer 发送并做有界等待，中断时恢复中断标志。
     *
     * @param record 待发送记录
     * @return 发送是否确认成功
     */
    private boolean sendWithTimeout(ProducerRecord<String, byte[]> record) {
        try {
            sharedProducer.send(record).get(config.sendTimeoutMs(), TimeUnit.MILLISECONDS);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("消息发送被中断: topic={}", record.topic(), e);
            return false;
        } catch (Exception e) {
            log.error("消息发送失败: topic={}", record.topic(), e);
            return false;
        }
    }

    /** 继承原记录的全部 header（含业务自定义 header）。 */
    private void copyHeaders(ConsumerRecord<String, byte[]> record, ProducerRecord<String, byte[]> target) {
        for (Header header : record.headers()) {
            target.headers().add(header.key(), header.value());
        }
    }

    /** 重投计数的键：topic-partition-offset；转存到重试 topic 后的副本另成一条键。 */
    private String recordKey(ConsumerRecord<String, byte[]> record) {
        return record.topic() + "-" + record.partition() + "-" + record.offset();
    }

    /**
     * 推导本次转存对应的 retry topic 往返轮次（1-based）：已有 x-retry-hop 则加一，缺失或损坏按第 1 轮。
     */
    private int nextHop(Headers headers) {
        Header header = headers.lastHeader(RETRY_HOP_HEADER);
        if (header == null) {
            return 1;
        }
        try {
            return Integer.parseInt(new String(header.value(), StandardCharsets.UTF_8)) + 1;
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    /** 取死信原因：异常为空（计数已达上限后的重入路径）时以 unknown 上报。 */
    private String reasonOf(Exception cause) {
        if (cause == null) {
            return "unknown";
        }
        return cause.getClass().getSimpleName();
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
        // 手动提交为实现约束，非配置项
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
