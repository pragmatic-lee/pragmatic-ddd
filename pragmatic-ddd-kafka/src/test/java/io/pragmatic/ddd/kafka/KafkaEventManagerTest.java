package io.pragmatic.ddd.kafka;

import io.pragmatic.ddd.event.EventException;
import io.pragmatic.ddd.event.IDomainEvent;
import io.pragmatic.ddd.event.PublishEventException;
import io.pragmatic.ddd.event.internal.model.DeliveryPolicy;
import io.pragmatic.ddd.event.internal.model.SubscribeData;
import io.pragmatic.ddd.event.spi.ITopicResolver;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * KafkaEventManager 发布与消费（重投/死信/延时策略/停机时序）逻辑测试。
 * 使用 kafka-clients 自带的 MockConsumer/MockProducer 作为测试替身，不依赖真实 broker。
 *
 * @author wizard-lee
 */
class KafkaEventManagerTest {

    private static final String TOPIC = "topic-a";

    private static final String RECONSUME_HEADER = "x-reconsume";

    private static final String RETRY_HOP_HEADER = "x-retry-hop";

    private ITopicResolver resolver() {
        return new ITopicResolver() {
            @Override
            public Set<String> getAllTopics() {
                return Set.of(TOPIC);
            }

            @Override
            public <T extends IDomainEvent> String resolveForType(Class<T> eventType) {
                return TOPIC;
            }
        };
    }

    private KafkaProperties config(int concurrency, int maxReconsume) {
        return configWith(concurrency, maxReconsume, "immediate", 10_000);
    }

    private KafkaProperties configWith(int concurrency, int maxReconsume, String delayedPolicy, int sendTimeoutMs) {
        return configWith(concurrency, maxReconsume, delayedPolicy, sendTimeoutMs, "100,500", "-retry", "1000,5000");
    }

    private KafkaProperties configWith(int concurrency,
                                   int maxReconsume,
                                   String delayedPolicy,
                                   int sendTimeoutMs,
                                   String inlineRetryIntervalsMs,
                                   String retryTopicSuffix,
                                   String retryHopBackoffMs) {
        return new KafkaProperties(
                "localhost:9092",
                "g1",
                "",
                500,
                1000,
                "latest",
                "all",
                "",
                true,
                "-dlq",
                maxReconsume,
                concurrency,
                sendTimeoutMs,
                delayedPolicy,
                10,
                "-delay",
                inlineRetryIntervalsMs,
                retryTopicSuffix,
                retryHopBackoffMs,
                30_000);
    }

    /** 重试链路用例配置：内联间隔压到 1ms、退避 100ms，缩短用例时长。 */
    private KafkaProperties retryConfig(int maxReconsume, String retryTopicSuffix) {
        return configWith(1, maxReconsume, "immediate", 50, "1,1", retryTopicSuffix, "100,100");
    }

    /** 批次预算用例配置：batchBudgetMs 压到 1ms，触发「超预算留待下轮」。 */
    private KafkaProperties budgetConfig(int maxReconsume) {
        return new KafkaProperties(
                "localhost:9092",
                "g1",
                "",
                500,
                1000,
                "latest",
                "all",
                "",
                true,
                "-dlq",
                maxReconsume,
                1,
                50,
                "immediate",
                10,
                "-delay",
                "1,1",
                "-retry",
                "100,100",
                1);
    }

    private SubscribeData sampleSubscribeData() {
        return new SubscribeData(
                "sub-1",
                "{\"eventId\":\"e1\",\"entityId\":\"k1\",\"occurredOn\":\"2026-01-01T00:00:00Z\","
                        + "\"operationCode\":\"OP\",\"version\":1}",
                "TestEvent",
                false,
                DeliveryPolicy.IMMEDIATE);
    }

    private SubscribeData delayedSubscribeData() {
        return new SubscribeData("sub-1", "{}", "TestEvent", false, DeliveryPolicy.DELAYED);
    }

    private MockProducer<String, byte[]> autoProducer() {
        return new MockProducer<>(true, new StringSerializer(), new ByteArraySerializer());
    }

    private ConsumerRecord<String, byte[]> record(String topic, long offset, String entityId) {
        String eventData = "{\"eventId\":\"e1\",\"entityId\":\"" + entityId
                + "\",\"occurredOn\":\"2026-01-01T00:00:00Z\",\"operationCode\":\"OP\",\"version\":1}";
        SubscribeData data = new SubscribeData("sub-1", eventData, "TestEvent", false, DeliveryPolicy.IMMEDIATE);
        byte[] value = new KafkaEventSerializer().serialize(data).getBytes(StandardCharsets.UTF_8);
        return new ConsumerRecord<>(topic, 0, offset, entityId, value);
    }

    private ConsumerRecord<String, byte[]> recordWithReconsume(String topic,
                                                              long offset,
                                                              String entityId,
                                                              int reconsumeTimes) {
        ConsumerRecord<String, byte[]> record = record(topic, offset, entityId);
        record.headers().add(RECONSUME_HEADER, String.valueOf(reconsumeTimes).getBytes(StandardCharsets.UTF_8));
        return record;
    }

    private String headerOf(ProducerRecord<String, byte[]> record, String key) {
        Header header = record.headers().lastHeader(key);
        assertThat(header).as("header %s 应存在", key).isNotNull();
        return new String(header.value(), StandardCharsets.UTF_8);
    }

    /** 注册一个必然失败的订阅者，返回其被调用次数计数器。 */
    private AtomicInteger failingSubscriber(KafkaEventManager manager) {
        AtomicInteger calls = new AtomicInteger();
        manager.registerSubscriber("sub-1", TestEvent.class, event -> {
            calls.incrementAndGet();
            throw new RuntimeException("boom");
        });
        return calls;
    }

    @Test
    void sendMessage_publishesRecordToTopic() throws Exception {
        MockProducer<String, byte[]> producer = new MockProducer<>(true, new StringSerializer(), new ByteArraySerializer());
        KafkaEventManager manager = KafkaEventManager.builder(config(1, 3), resolver())
                .producer(producer)
                .build();

        manager.sendMessage(sampleSubscribeData(), new TestEvent(), TOPIC);

        assertThat(producer.history()).hasSize(1);
        ProducerRecord<String, byte[]> sent = producer.history().get(0);
        assertThat(sent.topic()).isEqualTo(TOPIC);
        assertThat(sent.key()).isEqualTo("k1");
        assertThat(new String(sent.value())).contains("\"realEventName\":\"TestEvent\"");
    }

    @Test
    void sendMessage_delayedDowngradesToImmediateSend() throws Exception {
        MockProducer<String, byte[]> producer = new MockProducer<>(true, new StringSerializer(), new ByteArraySerializer());
        KafkaEventManager manager = KafkaEventManager.builder(config(1, 3), resolver())
                .producer(producer)
                .build();

        manager.sendMessage(delayedSubscribeData(), new TestEvent(), TOPIC);

        assertThat(producer.history()).hasSize(1);
        assertThat(producer.history().get(0).topic()).isEqualTo(TOPIC);
    }

    @Test
    void sendMessage_delayedRejectThrowsEventException() {
        MockProducer<String, byte[]> producer = new MockProducer<>(true, new StringSerializer(), new ByteArraySerializer());
        KafkaEventManager manager = KafkaEventManager.builder(configWith(1, 3, "reject", 10_000), resolver())
                .producer(producer)
                .build();

        assertThatThrownBy(() -> manager.sendMessage(delayedSubscribeData(), new TestEvent(), TOPIC))
                .isInstanceOf(EventException.class);
        assertThat(producer.history()).isEmpty();
    }

    @Test
    void sendMessage_delayedRelayStoresToDelayTopicWithHeaders() throws Exception {
        MockProducer<String, byte[]> producer = new MockProducer<>(true, new StringSerializer(), new ByteArraySerializer());
        KafkaEventManager manager = KafkaEventManager.builder(configWith(1, 3, "relay", 10_000), resolver())
                .producer(producer)
                .build();
        long before = System.currentTimeMillis();

        manager.sendMessage(delayedSubscribeData(), new TestEvent(), TOPIC);
        long after = System.currentTimeMillis();

        assertThat(producer.history()).hasSize(1);
        ProducerRecord<String, byte[]> sent = producer.history().get(0);
        assertThat(sent.topic()).isEqualTo(TOPIC + "-delay");
        assertThat(sent.key()).isEqualTo("k1");
        assertThat(new String(sent.headers().lastHeader("x-origin-topic").value(), StandardCharsets.UTF_8))
                .isEqualTo(TOPIC);
        long deliverAt = Long.parseLong(
                new String(sent.headers().lastHeader("x-deliver-at").value(), StandardCharsets.UTF_8));
        assertThat(deliverAt).isBetween(before + 8_000, after + 12_000);
        assertThat(producer.history().stream().noneMatch(r -> r.topic().equals(TOPIC))).isTrue();
    }

    @Test
    void sendMessage_publishTimeoutThrowsPublishEventException() {
        MockProducer<String, byte[]> producer = new MockProducer<>(false, new StringSerializer(), new ByteArraySerializer());
        KafkaEventManager manager = KafkaEventManager.builder(configWith(1, 3, "immediate", 50), resolver())
                .producer(producer)
                .build();

        assertThatThrownBy(() -> manager.sendMessage(sampleSubscribeData(), new TestEvent(), TOPIC))
                .isInstanceOf(PublishEventException.class);
    }

    @Test
    void sendMessage_interruptRestoresInterruptFlagAndThrows() {
        MockProducer<String, byte[]> producer = new MockProducer<>(false, new StringSerializer(), new ByteArraySerializer());
        KafkaEventManager manager = KafkaEventManager.builder(configWith(1, 3, "immediate", 10_000), resolver())
                .producer(producer)
                .build();

        Thread.currentThread().interrupt();
        try {
            assertThatThrownBy(() -> manager.sendMessage(sampleSubscribeData(), new TestEvent(), TOPIC))
                    .isInstanceOf(PublishEventException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void handleRecord_inlineRetriesThenForwardsToRetryTopic() {
        MockProducer<String, byte[]> producer = autoProducer();
        KafkaEventManager manager = KafkaEventManager.builder(retryConfig(6, "-retry"), resolver())
                .producer(producer)
                .build();
        // 直接驱动 handleRecord 时置运行标志：内联重试等待的分片检查以运行态为前提
        manager.running.set(true);
        AtomicInteger calls = failingSubscriber(manager);
        long before = System.currentTimeMillis();

        assertThat(manager.handleRecord(record(TOPIC, 0, "k1"))).isEqualTo(KafkaEventManager.HandleResult.COMMIT);
        long after = System.currentTimeMillis();

        // 首投 + 2 次内联重试后本轮预算耗尽 → 转 retry topic，同批继续处理下一条
        assertThat(calls.get()).isEqualTo(3);
        assertThat(producer.history()).hasSize(1);
        ProducerRecord<String, byte[]> sent = producer.history().get(0);
        assertThat(sent.topic()).isEqualTo(TOPIC + "-retry");
        assertThat(sent.key()).isEqualTo("k1");
        assertThat(headerOf(sent, RECONSUME_HEADER)).isEqualTo("3");
        assertThat(headerOf(sent, RETRY_HOP_HEADER)).isEqualTo("1");
        assertThat(headerOf(sent, KafkaRedeliverRelay.ORIGIN_TOPIC_HEADER)).isEqualTo(TOPIC);
        long deliverAt = Long.parseLong(headerOf(sent, KafkaRedeliverRelay.DELIVER_AT_HEADER));
        assertThat(deliverAt).isBetween(before + 100, after + 100);
    }

    @Test
    void handleRecord_inlineRetryThenSuccessResetsCounter() {
        MockProducer<String, byte[]> producer = autoProducer();
        KafkaEventManager manager = KafkaEventManager.builder(retryConfig(6, "-retry"), resolver())
                .producer(producer)
                .build();
        // 直接驱动 handleRecord 时置运行标志：内联重试等待的分片检查以运行态为前提
        manager.running.set(true);
        AtomicInteger calls = new AtomicInteger();
        manager.registerSubscriber("sub-1", TestEvent.class, event -> {
            if (calls.incrementAndGet() < 3) {
                throw new RuntimeException("boom");
            }
        });

        assertThat(manager.handleRecord(record(TOPIC, 0, "k1"))).isEqualTo(KafkaEventManager.HandleResult.COMMIT);
        assertThat(calls.get()).isEqualTo(3);
        assertThat(producer.history()).isEmpty();

        // 成功即清理计数：重入同一条记录按 x-reconsume(0) 重新起算，只需 1 次调用即成功
        assertThat(manager.handleRecord(record(TOPIC, 0, "k1"))).isEqualTo(KafkaEventManager.HandleResult.COMMIT);
        assertThat(calls.get()).isEqualTo(4);
        assertThat(producer.history()).isEmpty();
    }

    @Test
    void handleRecord_reachesAttemptLimitForwardsToDlq() {
        MockProducer<String, byte[]> producer = autoProducer();
        KafkaEventManager manager = KafkaEventManager.builder(retryConfig(2, "-retry"), resolver())
                .producer(producer)
                .build();
        // 直接驱动 handleRecord 时置运行标志：内联重试等待的分片检查以运行态为前提
        manager.running.set(true);
        AtomicInteger calls = failingSubscriber(manager);

        // attemptLimit = maxReconsume + 1 = 3，累计失败已达 2 → 本次失败即达上限，直接转死信
        assertThat(manager.handleRecord(recordWithReconsume(TOPIC, 0, "k1", 2)))
                .isEqualTo(KafkaEventManager.HandleResult.COMMIT);

        assertThat(calls.get()).isEqualTo(1);
        assertThat(producer.history()).hasSize(1);
        assertThat(producer.history().get(0).topic()).isEqualTo(TOPIC + "-dlq");
        assertThat(headerOf(producer.history().get(0), RECONSUME_HEADER)).isEqualTo("3");
    }

    @Test
    void handleRecord_reentryAfterDlqForwardFailureDoesNotCallSubscriber() {
        MockProducer<String, byte[]> producer = new MockProducer<>(false, new StringSerializer(), new ByteArraySerializer());
        KafkaEventManager manager = KafkaEventManager.builder(retryConfig(2, "-retry"), resolver())
                .producer(producer)
                .build();
        // 直接驱动 handleRecord 时置运行标志：内联重试等待的分片检查以运行态为前提
        manager.running.set(true);
        AtomicInteger calls = failingSubscriber(manager);
        ConsumerRecord<String, byte[]> record = recordWithReconsume(TOPIC, 0, "k1", 2);

        // 计数已达上限 → 走死信转发，转发未确认（超时）→ RETRY，消息保留
        assertThat(manager.handleRecord(record)).isEqualTo(KafkaEventManager.HandleResult.RETRY);
        assertThat(calls.get()).isEqualTo(1);
        assertThat(producer.completeNext()).isTrue();
        assertThat(producer.history()).hasSize(1);
        assertThat(producer.history().get(0).topic()).isEqualTo(TOPIC + "-dlq");

        // 重入：计数已达上限，不再调用订阅者，直接重试死信转发
        assertThat(manager.handleRecord(record)).isEqualTo(KafkaEventManager.HandleResult.RETRY);
        assertThat(calls.get()).isEqualTo(1);
        assertThat(producer.completeNext()).isTrue();
    }

    @Test
    void handleRecord_retryTopicForwardFailureKeepsRetrying() {
        MockProducer<String, byte[]> producer = new MockProducer<>(false, new StringSerializer(), new ByteArraySerializer());
        KafkaEventManager manager = KafkaEventManager.builder(retryConfig(6, "-retry"), resolver())
                .producer(producer)
                .build();
        // 直接驱动 handleRecord 时置运行标志：内联重试等待的分片检查以运行态为前提
        manager.running.set(true);
        AtomicInteger calls = failingSubscriber(manager);
        ConsumerRecord<String, byte[]> record = record(TOPIC, 0, "k1");

        // 转存已发出但未确认（MockProducer(false) 的 history 立即含该记录，completion 待 completeNext）
        assertThat(manager.handleRecord(record)).isEqualTo(KafkaEventManager.HandleResult.RETRY);
        assertThat(calls.get()).isEqualTo(3);
        assertThat(producer.completeNext()).isTrue();
        assertThat(producer.history()).hasSize(1);
        assertThat(producer.history().get(0).topic()).isEqualTo(TOPIC + "-retry");
        assertThat(headerOf(producer.history().get(0), RECONSUME_HEADER)).isEqualTo("3");

        // counter 保留：重入后继续消耗本轮剩余预算（times 4、5 内联，6 再次转存）
        assertThat(manager.handleRecord(record)).isEqualTo(KafkaEventManager.HandleResult.RETRY);
        assertThat(calls.get()).isEqualTo(6);
        assertThat(producer.completeNext()).isTrue();
        assertThat(producer.history()).hasSize(2);
        assertThat(headerOf(producer.history().get(1), RECONSUME_HEADER)).isEqualTo("6");
    }

    @Test
    void handleRecord_retryChannelDisabledForwardsToDlqDirectly() {
        MockProducer<String, byte[]> producer = autoProducer();
        KafkaEventManager manager = KafkaEventManager.builder(retryConfig(6, ""), resolver())
                .producer(producer)
                .build();
        // 直接驱动 handleRecord 时置运行标志：内联重试等待的分片检查以运行态为前提
        manager.running.set(true);
        AtomicInteger calls = failingSubscriber(manager);

        assertThat(manager.handleRecord(record(TOPIC, 0, "k1"))).isEqualTo(KafkaEventManager.HandleResult.COMMIT);

        assertThat(calls.get()).isEqualTo(3);
        assertThat(producer.history()).hasSize(1);
        assertThat(producer.history().get(0).topic()).isEqualTo(TOPIC + "-dlq");
        assertThat(producer.history().stream().noneMatch(r -> r.topic().endsWith("-retry"))).isTrue();
    }

    @Test
    void handleRecord_boundaryForMaxReconsumeThreeRetriesOnceThenDlq() {
        MockProducer<String, byte[]> producer = autoProducer();
        KafkaEventManager manager = KafkaEventManager.builder(retryConfig(3, "-retry"), resolver())
                .producer(producer)
                .build();
        // 直接驱动 handleRecord 时置运行标志：内联重试等待的分片检查以运行态为前提
        manager.running.set(true);
        AtomicInteger calls = failingSubscriber(manager);

        // attemptLimit = 4：第 3 次失败转 retry topic
        assertThat(manager.handleRecord(record(TOPIC, 0, "k1"))).isEqualTo(KafkaEventManager.HandleResult.COMMIT);
        assertThat(calls.get()).isEqualTo(3);
        assertThat(producer.history()).hasSize(1);
        assertThat(producer.history().get(0).topic()).isEqualTo(TOPIC + "-retry");
        assertThat(headerOf(producer.history().get(0), RECONSUME_HEADER)).isEqualTo("3");

        // 回投后被再次消费（新记录携带 x-reconsume=3）：第 4 次失败即达上限 → 转死信
        assertThat(manager.handleRecord(recordWithReconsume(TOPIC, 0, "k1", 3)))
                .isEqualTo(KafkaEventManager.HandleResult.COMMIT);
        assertThat(calls.get()).isEqualTo(4);
        assertThat(producer.history()).hasSize(2);
        assertThat(producer.history().get(1).topic()).isEqualTo(TOPIC + "-dlq");
        assertThat(headerOf(producer.history().get(1), RECONSUME_HEADER)).isEqualTo("4");
    }

    @Test
    void handleRecord_backoffAbortedByStopReturnsRetry() {
        MockProducer<String, byte[]> producer = autoProducer();
        BackoffRecordingManager manager = new BackoffRecordingManager(retryConfig(6, "-retry"), resolver(), producer, false);
        AtomicInteger calls = failingSubscriber(manager);

        assertThat(manager.handleRecord(record(TOPIC, 0, "k1"))).isEqualTo(KafkaEventManager.HandleResult.RETRY);

        assertThat(calls.get()).isEqualTo(1);
        assertThat(manager.backoffTimes()).containsExactly(1);
        assertThat(producer.history()).isEmpty();
    }

    @Test
    void handleRecord_backoffTakesRoundPositionEachRound() {
        MockProducer<String, byte[]> producer = autoProducer();
        BackoffRecordingManager manager = new BackoffRecordingManager(retryConfig(6, "-retry"), resolver(), producer, true);
        failingSubscriber(manager);

        assertThat(manager.handleRecord(record(TOPIC, 0, "k1"))).isEqualTo(KafkaEventManager.HandleResult.COMMIT);
        assertThat(manager.handleRecord(recordWithReconsume(TOPIC, 0, "k1", 3)))
                .isEqualTo(KafkaEventManager.HandleResult.COMMIT);

        // 每轮的轮内位置从 1 重新起算：间隔表按位置取档，第 2 轮仍取到第 1 档
        assertThat(manager.backoffTimes()).containsExactly(1, 2, 4, 5);
        assertThat(producer.history()).hasSize(2);
    }

    @Test
    void pollLoop_continuesSamePartitionAfterRetryTransfer() {
        MockProducer<String, byte[]> producer = autoProducer();
        KafkaEventManager manager = KafkaEventManager.builder(retryConfig(6, "-retry"), resolver())
                .producer(producer)
                .build();
        List<String> received = new ArrayList<>();
        manager.registerSubscriber("sub-1", TestEvent.class, event -> {
            if ("bad".equals(event.getEntityId())) {
                throw new RuntimeException("boom");
            }
            received.add(event.getEntityId());
        });
        TopicPartition tp = new TopicPartition(TOPIC, 0);
        OneBatchConsumer consumer = new OneBatchConsumer();
        consumer.assign(List.of(tp));
        consumer.updateBeginningOffsets(Map.of(tp, 0L));
        consumer.updateEndOffsets(Map.of(tp, 3L));
        consumer.addRecord(record(TOPIC, 0, "bad"));
        consumer.addRecord(record(TOPIC, 1, "ok1"));
        consumer.addRecord(record(TOPIC, 2, "ok2"));

        manager.running.set(true);
        manager.pollLoop(consumer);

        // 第 1 条失败并转存重试 topic 后，同分区后续消息继续处理，位移正常提交
        assertThat(received).containsExactly("ok1", "ok2");
        assertThat(consumer.events()).doesNotContain("seek");
        assertThat(consumer.committed(tp)).isNotNull();
        assertThat(consumer.committed(tp).offset()).isEqualTo(3);
        assertThat(producer.history()).hasSize(1);
        assertThat(producer.history().get(0).topic()).isEqualTo(TOPIC + "-retry");
    }

    @Test
    void pollLoop_batchBudgetSeeksRemainingRecords() {
        MockProducer<String, byte[]> producer = autoProducer();
        KafkaEventManager manager = KafkaEventManager.builder(budgetConfig(6), resolver())
                .producer(producer)
                .build();
        // 直接驱动 handleRecord 时置运行标志：内联重试等待的分片检查以运行态为前提
        manager.running.set(true);
        AtomicInteger calls = failingSubscriber(manager);
        TopicPartition tp = new TopicPartition(TOPIC, 0);
        OneBatchConsumer consumer = new OneBatchConsumer();
        consumer.assign(List.of(tp));
        consumer.updateBeginningOffsets(Map.of(tp, 0L));
        consumer.updateEndOffsets(Map.of(tp, 3L));
        consumer.addRecord(record(TOPIC, 0, "bad"));
        consumer.addRecord(record(TOPIC, 1, "bad"));
        consumer.addRecord(record(TOPIC, 2, "bad"));

        manager.running.set(true);
        manager.pollLoop(consumer);

        // 预算 1ms：只处理第 1 条，剩余消息 seek 回退且本分区不提交
        assertThat(calls.get()).isEqualTo(3);
        assertThat(consumer.events()).contains("seek:1");
        assertThat(consumer.committed(tp)).isNull();
    }

    @Test
    void start_assemblesRetryRelayOnlyWhenRetryChannelEnabled() {
        MockProducer<String, byte[]> producer = autoProducer();
        TestableManager withRetry = new TestableManager(retryConfig(6, "-retry"), resolver(), producer);
        withRetry.start();
        try {
            // 重试回投的启停只看 retry-topic-suffix，与 delayed-policy 解耦
            assertThat(withRetry.retryRelay).isNotNull();
            assertThat(withRetry.delayRelay).isNull();
        } finally {
            withRetry.shutdown();
        }

        TestableManager withoutRetry = new TestableManager(retryConfig(6, ""), resolver(), producer);
        withoutRetry.start();
        try {
            assertThat(withoutRetry.retryRelay).isNull();
            assertThat(withoutRetry.delayRelay).isNull();
        } finally {
            withoutRetry.shutdown();
        }
    }

    @Test
    void initializeTopics_createsConsumersByConcurrency() {
        TestableManager manager = new TestableManager(config(3, 3), resolver());
        manager.triggerInitTopics();
        assertThat(manager.createdConsumerCount()).isEqualTo(3);
    }

    @Test
    void revokedCallbackCommitsOffsetsBeforeRebalance() {
        TestableManager manager = new TestableManager(config(1, 3), resolver());
        manager.triggerInitTopics();

        assertThat(manager.lastListener()).isNotNull();
        manager.lastListener().onPartitionsRevoked(Set.of(new TopicPartition(TOPIC, 0)));

        for (RecordingConsumer consumer : manager.createdConsumers()) {
            assertThat(consumer.events()).contains("commitSync");
        }
    }

    @Test
    void shutdown_wakesUpConsumersBeforeClose() throws Exception {
        TestableManager manager = new TestableManager(config(1, 3), resolver());
        manager.start();
        Thread.sleep(200);
        manager.shutdown();

        assertThat(manager.createdConsumers()).isNotEmpty();
        for (RecordingConsumer consumer : manager.createdConsumers()) {
            assertThat(consumer.events()).contains("wakeup", "close");
            assertThat(consumer.events().indexOf("wakeup")).isLessThan(consumer.events().indexOf("close"));
        }
    }

    /** 记录关键调用顺序的 MockConsumer 替身，用于校验停机时序、rebalance 提交与位移回退。 */
    static class RecordingConsumer extends MockConsumer<String, byte[]> {

        private final List<String> events = new ArrayList<>();

        private ConsumerRebalanceListener listener;

        RecordingConsumer() {
            super(OffsetResetStrategy.EARLIEST);
        }

        List<String> events() {
            return events;
        }

        ConsumerRebalanceListener listener() {
            return listener;
        }

        @Override
        public void subscribe(Collection<String> topics, ConsumerRebalanceListener listener) {
            super.subscribe(topics, listener);
            this.listener = listener;
        }

        @Override
        public void wakeup() {
            events.add("wakeup");
        }

        @Override
        public void close() {
            events.add("close");
        }

        @Override
        public void pause(Collection<TopicPartition> partitions) {
            events.add("pause");
        }

        @Override
        public void resume(Collection<TopicPartition> partitions) {
            events.add("resume");
        }

        @Override
        public void commitSync() {
            events.add("commitSync");
        }

        @Override
        public synchronized void commitSync(Map<TopicPartition, OffsetAndMetadata> offsets) {
            events.add("commitSync");
            super.commitSync(offsets);
        }

        @Override
        public synchronized void seek(TopicPartition partition, long offset) {
            events.add("seek:" + offset);
            super.seek(partition, offset);
        }
    }

    /** 首次 poll 返回已投递记录、之后再 poll 抛 WakeupException 的替身，用于驱动一轮 pollLoop 后自然退出。 */
    static final class OneBatchConsumer extends RecordingConsumer {

        private int polls = 0;

        @Override
        public synchronized ConsumerRecords<String, byte[]> poll(Duration timeout) {
            polls++;
            if (polls > 1) {
                throw new WakeupException();
            }
            return super.poll(timeout);
        }
    }

    /** 记录内联重试等待次数并可指定等待结果的测试子类，用于断言退避取档与放弃语义。 */
    static final class BackoffRecordingManager extends KafkaEventManager {

        private final List<Integer> backoffTimes = new ArrayList<>();

        private final boolean backoffResult;

        BackoffRecordingManager(KafkaProperties config,
                                ITopicResolver resolver,
                                Producer<String, byte[]> producer,
                                boolean backoffResult) {
            super(KafkaEventManager.builder(config, resolver).producer(producer));
            this.backoffResult = backoffResult;
        }

        List<Integer> backoffTimes() {
            return backoffTimes;
        }

        @Override
        boolean awaitRetryBackoff(int times) {
            backoffTimes.add(times);
            return backoffResult;
        }
    }

    /** 可注入并计数 MockConsumer 的测试子类，用于校验按 concurrency 创建消费者与生命周期时序。 */
    static final class TestableManager extends KafkaEventManager {

        private final List<RecordingConsumer> createdConsumers = new ArrayList<>();

        private final AtomicInteger created = new AtomicInteger(0);

        TestableManager(KafkaProperties config, ITopicResolver resolver) {
            this(config, resolver, null);
        }

        TestableManager(KafkaProperties config, ITopicResolver resolver, Producer<String, byte[]> producer) {
            super(KafkaEventManager.builder(config, resolver).producer(producer));
        }

        void triggerInitTopics() {
            initTopics();
        }

        int createdConsumerCount() {
            return created.get();
        }

        List<RecordingConsumer> createdConsumers() {
            return createdConsumers;
        }

        ConsumerRebalanceListener lastListener() {
            return createdConsumers.isEmpty() ? null : createdConsumers.get(0).listener();
        }

        @Override
        protected Consumer<String, byte[]> buildConsumer() {
            created.incrementAndGet();
            RecordingConsumer consumer = new RecordingConsumer();
            createdConsumers.add(consumer);
            return consumer;
        }
    }

    /** 测试领域事件，公共字段 + 无参构造以满足 fastjson2 反序列化需求。 */
    static final class TestEvent implements IDomainEvent {
        public String eventId = "e1";
        public String entityId = "k1";
        public Instant occurredOn = Instant.parse("2026-01-01T00:00:00Z");
        public String operationCode = "OP";
        public long version = 1;

        @Override
        public String getEventId() {
            return eventId;
        }

        @Override
        public String getEntityId() {
            return entityId;
        }

        @Override
        public Instant getOccurredOn() {
            return occurredOn;
        }

        @Override
        public String getOperationCode() {
            return operationCode;
        }

        @Override
        public long getVersion() {
            return version;
        }
    }
}
