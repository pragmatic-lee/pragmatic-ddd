package io.pragmatic.ddd.kafka;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * KafkaRedeliverRelay 回投逻辑测试：到期回投（原 key/原 topic）、未到期 pause、
 * 到期 resume、回投失败位移不提交、header 缺失即时回投，以及重试链路（retry）的后缀与标识参数化。
 * 使用 MockConsumer/MockProducer 测试替身，直接驱动包级处理方法，不依赖真实 broker。
 *
 * @author wizard-lee
 */
class KafkaRedeliverRelayTest {

    private static final String TOPIC = "topic-a";

    private static final String DELAY_TOPIC = "topic-a-delay";

    private static final String RETRY_TOPIC = "topic-a-retry";

    private static final TopicPartition DELAY_PARTITION = new TopicPartition(DELAY_TOPIC, 0);

    private static final TopicPartition RETRY_PARTITION = new TopicPartition(RETRY_TOPIC, 0);

    private KafkaProperties config() {
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
                16,
                1,
                50,
                "relay",
                10,
                "-delay",
                "100,500",
                "-retry",
                "1000,5000",
                30000);
    }

    private KafkaRedeliverRelay relay(MockProducer<String, byte[]> producer) {
        return new KafkaRedeliverRelay(
                producer,
                config(),
                Set.of(TOPIC),
                KafkaRedeliverRelay.LABEL_DELAY,
                "-delay");
    }

    private KafkaRedeliverRelay retryRelay(MockProducer<String, byte[]> producer) {
        return new KafkaRedeliverRelay(
                producer,
                config(),
                Set.of(TOPIC),
                KafkaRedeliverRelay.LABEL_RETRY,
                "-retry");
    }

    private ConsumerRecord<String, byte[]> delayRecord(long deliverAt) {
        ConsumerRecord<String, byte[]> record =
                new ConsumerRecord<>(DELAY_TOPIC, 0, 0, "k1", "body".getBytes(StandardCharsets.UTF_8));
        record.headers().add(KafkaRedeliverRelay.DELIVER_AT_HEADER,
                Long.toString(deliverAt).getBytes(StandardCharsets.UTF_8));
        record.headers().add(KafkaRedeliverRelay.ORIGIN_TOPIC_HEADER, TOPIC.getBytes(StandardCharsets.UTF_8));
        return record;
    }

    @Test
    void processPartition_dueHeadRedeliversToOriginTopicAndCommits() {
        MockProducer<String, byte[]> producer =
                new MockProducer<>(true, new StringSerializer(), new ByteArraySerializer());
        KafkaRedeliverRelay relay = relay(producer);
        RecordingConsumer consumer = new RecordingConsumer();
        long deliverAt = System.currentTimeMillis() - 1000;

        relay.processPartition(consumer, DELAY_PARTITION, List.of(delayRecord(deliverAt)), new HashMap<>());

        assertThat(producer.history()).hasSize(1);
        assertThat(producer.history().get(0).topic()).isEqualTo(TOPIC);
        assertThat(producer.history().get(0).key()).isEqualTo("k1");
        assertThat(consumer.events()).contains("commitSync");
        assertThat(consumer.events()).doesNotContain("pause");
    }

    @Test
    void processPartition_futureHeadPausesPartitionAndResumesWhenDue() {
        MockProducer<String, byte[]> producer =
                new MockProducer<>(true, new StringSerializer(), new ByteArraySerializer());
        KafkaRedeliverRelay relay = relay(producer);
        RecordingConsumer consumer = new RecordingConsumer();
        Map<TopicPartition, Long> pausedDue = new HashMap<>();
        long deliverAt = System.currentTimeMillis() + 60_000;

        relay.processPartition(consumer, DELAY_PARTITION, List.of(delayRecord(deliverAt)), pausedDue);

        assertThat(consumer.events()).contains("pause");
        assertThat(pausedDue).containsEntry(DELAY_PARTITION, deliverAt);
        assertThat(producer.history()).isEmpty();
        assertThat(consumer.events()).doesNotContain("commitSync");

        pausedDue.put(DELAY_PARTITION, System.currentTimeMillis() - 1);
        relay.resumeDuePartitions(consumer, pausedDue);

        assertThat(consumer.events()).contains("resume");
        assertThat(pausedDue).isEmpty();
    }

    @Test
    void processPartition_futureHeadSeeksBackBeforePausing() {
        MockProducer<String, byte[]> producer =
                new MockProducer<>(true, new StringSerializer(), new ByteArraySerializer());
        KafkaRedeliverRelay relay = relay(producer);
        RecordingConsumer consumer = new RecordingConsumer();
        long deliverAt = System.currentTimeMillis() + 60_000;

        relay.processPartition(consumer, DELAY_PARTITION, List.of(delayRecord(deliverAt)), new HashMap<>());

        // pause 只停止拉取、不回退拉取位置：必须先 seek 回头部位移，resume 后该消息才会被重新返回
        assertThat(consumer.events()).contains("seek:0", "pause");
        assertThat(consumer.events().indexOf("seek:0")).isLessThan(consumer.events().indexOf("pause"));
    }

    @Test
    void processPartition_redeliverCarriesBusinessHeadersButDropsRelayHeaders() {
        MockProducer<String, byte[]> producer =
                new MockProducer<>(true, new StringSerializer(), new ByteArraySerializer());
        KafkaRedeliverRelay relay = retryRelay(producer);
        RecordingConsumer consumer = new RecordingConsumer();
        ConsumerRecord<String, byte[]> record = new ConsumerRecord<>(
                RETRY_TOPIC, 0, 0, "k1", "body".getBytes(StandardCharsets.UTF_8));
        record.headers().add(KafkaRedeliverRelay.DELIVER_AT_HEADER,
                Long.toString(System.currentTimeMillis() - 1000).getBytes(StandardCharsets.UTF_8));
        record.headers().add(KafkaRedeliverRelay.ORIGIN_TOPIC_HEADER, TOPIC.getBytes(StandardCharsets.UTF_8));
        record.headers().add("biz-header", "biz".getBytes(StandardCharsets.UTF_8));
        record.headers().add("x-reconsume", "3".getBytes(StandardCharsets.UTF_8));
        record.headers().add("x-retry-hop", "1".getBytes(StandardCharsets.UTF_8));

        relay.processPartition(consumer, RETRY_PARTITION, List.of(record), new HashMap<>());

        // 回投需继承业务 header 与计数/轮次 header（否则 x-reconsume 归零、x-retry-hop 永远停在 1），
        // 仅回投链路自用的 x-deliver-at / x-origin-topic 不随业务消息回流
        ProducerRecord<String, byte[]> out = producer.history().get(0);
        assertThat(new String(out.headers().lastHeader("biz-header").value(), StandardCharsets.UTF_8))
                .isEqualTo("biz");
        assertThat(new String(out.headers().lastHeader("x-reconsume").value(), StandardCharsets.UTF_8))
                .isEqualTo("3");
        assertThat(new String(out.headers().lastHeader("x-retry-hop").value(), StandardCharsets.UTF_8))
                .isEqualTo("1");
        assertThat(out.headers().lastHeader(KafkaRedeliverRelay.DELIVER_AT_HEADER)).isNull();
        assertThat(out.headers().lastHeader(KafkaRedeliverRelay.ORIGIN_TOPIC_HEADER)).isNull();
    }

    @Test
    void processPartition_redeliverFailureSkipsCommitForRetry() {
        MockProducer<String, byte[]> producer =
                new MockProducer<>(false, new StringSerializer(), new ByteArraySerializer());
        KafkaRedeliverRelay relay = relay(producer);
        RecordingConsumer consumer = new RecordingConsumer();
        long deliverAt = System.currentTimeMillis() - 1000;

        relay.processPartition(consumer, DELAY_PARTITION, List.of(delayRecord(deliverAt)), new HashMap<>());

        // 回投已发出但未确认 → 位移不提交，下轮重试
        assertThat(consumer.events()).doesNotContain("commitSync");
        assertThat(producer.history()).hasSize(1);
    }

    @Test
    void processPartition_missingHeaderRedeliversImmediately() {
        MockProducer<String, byte[]> producer =
                new MockProducer<>(true, new StringSerializer(), new ByteArraySerializer());
        KafkaRedeliverRelay relay = relay(producer);
        RecordingConsumer consumer = new RecordingConsumer();
        ConsumerRecord<String, byte[]> record =
                new ConsumerRecord<>(DELAY_TOPIC, 0, 0, "k1", "body".getBytes(StandardCharsets.UTF_8));

        relay.processPartition(consumer, DELAY_PARTITION, List.of(record), new HashMap<>());

        // header 缺失 → 视为到期立即回投，目标按延时 topic 名反解
        assertThat(producer.history()).hasSize(1);
        assertThat(producer.history().get(0).topic()).isEqualTo(TOPIC);
        assertThat(producer.history().get(0).key()).isEqualTo("k1");
        assertThat(consumer.events()).contains("commitSync");
    }

    @Test
    void nextPollTimeoutClampsToNearestDueAndPollTimeout() {
        MockProducer<String, byte[]> producer =
                new MockProducer<>(true, new StringSerializer(), new ByteArraySerializer());
        KafkaRedeliverRelay relay = relay(producer);

        assertThat(relay.nextPollTimeout(new HashMap<>())).isEqualTo(1000);

        Map<TopicPartition, Long> pausedDue = new HashMap<>();
        pausedDue.put(DELAY_PARTITION, System.currentTimeMillis() + 60_000);
        assertThat(relay.nextPollTimeout(pausedDue)).isEqualTo(1000);

        pausedDue.put(DELAY_PARTITION, System.currentTimeMillis() + 100);
        assertThat(relay.nextPollTimeout(pausedDue)).isLessThanOrEqualTo(100);
    }

    @Test
    void processPartition_retryRelayRedeliversRetryTopicToOriginTopic() {
        MockProducer<String, byte[]> producer =
                new MockProducer<>(true, new StringSerializer(), new ByteArraySerializer());
        KafkaRedeliverRelay relay = retryRelay(producer);
        RecordingConsumer consumer = new RecordingConsumer();
        ConsumerRecord<String, byte[]> record =
                new ConsumerRecord<>(RETRY_TOPIC, 0, 0, "k1", "body".getBytes(StandardCharsets.UTF_8));
        record.headers().add(KafkaRedeliverRelay.DELIVER_AT_HEADER,
                Long.toString(System.currentTimeMillis() - 1000).getBytes(StandardCharsets.UTF_8));
        record.headers().add(KafkaRedeliverRelay.ORIGIN_TOPIC_HEADER, TOPIC.getBytes(StandardCharsets.UTF_8));

        relay.processPartition(consumer, RETRY_PARTITION, List.of(record), new HashMap<>());

        assertThat(producer.history()).hasSize(1);
        assertThat(producer.history().get(0).topic()).isEqualTo(TOPIC);
        assertThat(producer.history().get(0).key()).isEqualTo("k1");
        assertThat(consumer.events()).contains("commitSync");
    }

    @Test
    void processPartition_retryRelayMissingOriginTopicStripsInjectedSuffix() {
        MockProducer<String, byte[]> producer =
                new MockProducer<>(true, new StringSerializer(), new ByteArraySerializer());
        KafkaRedeliverRelay relay = retryRelay(producer);
        RecordingConsumer consumer = new RecordingConsumer();
        ConsumerRecord<String, byte[]> record =
                new ConsumerRecord<>(RETRY_TOPIC, 0, 0, "k1", "body".getBytes(StandardCharsets.UTF_8));

        relay.processPartition(consumer, RETRY_PARTITION, List.of(record), new HashMap<>());

        // x-origin-topic 缺失 → 按注入的 retry 后缀反解回投目标（不是硬编码的 -delay）
        assertThat(producer.history()).hasSize(1);
        assertThat(producer.history().get(0).topic()).isEqualTo(TOPIC);
        assertThat(consumer.events()).contains("commitSync");
    }

    /** 记录 pause/resume/commitSync 等关键调用的 MockConsumer 替身。 */
    static final class RecordingConsumer extends MockConsumer<String, byte[]> {

        private final List<String> events = new ArrayList<>();

        RecordingConsumer() {
            super(OffsetResetStrategy.EARLIEST);
            // 回投器在暂停未到期分区前会 seek 回退拉取位置，MockConsumer 要求分区已分配
            assign(List.of(DELAY_PARTITION, RETRY_PARTITION));
        }

        List<String> events() {
            return events;
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
}
