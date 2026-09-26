package io.pragmatic.ddd.kafka;

import io.pragmatic.ddd.event.IDomainEvent;
import io.pragmatic.ddd.event.internal.model.DeliveryPolicy;
import io.pragmatic.ddd.event.internal.model.SubscribeData;
import io.pragmatic.ddd.event.spi.ITopicResolver;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * KafkaEventManager 发布与消费（重投/死信）逻辑测试。
 * 使用 kafka-clients 自带的 MockConsumer/MockProducer 作为测试替身，不依赖真实 broker。
 *
 * @author wizard-lee
 */
class KafkaEventManagerTest {

    private static final String TOPIC = "topic-a";

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

    private KafkaConfig config(int concurrency, int maxReconsume) {
        return new KafkaConfig(
                "localhost:9092",
                "g1",
                "",
                false,
                500,
                1000,
                "latest",
                "all",
                "",
                true,
                "-dlq",
                maxReconsume,
                concurrency);
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

        SubscribeData delayed = new SubscribeData("sub-1", "{}", "TestEvent", false, DeliveryPolicy.DELAYED);
        manager.sendMessage(delayed, new TestEvent(), TOPIC);

        assertThat(producer.history()).hasSize(1);
        assertThat(producer.history().get(0).topic()).isEqualTo(TOPIC);
    }

    @Test
    void handleRecord_retriesThenForwardsToDlq() {
        MockProducer<String, byte[]> producer = new MockProducer<>(true, new StringSerializer(), new ByteArraySerializer());
        KafkaEventManager manager = KafkaEventManager.builder(config(1, 3), resolver())
                .producer(producer)
                .build();

        manager.registerSubscriber("sub-1", TestEvent.class, event -> {
            throw new RuntimeException("boom");
        });

        byte[] value = new KafkaEventSerializer().serialize(sampleSubscribeData()).getBytes();
        ConsumerRecord<String, byte[]> record = new ConsumerRecord<>(TOPIC, 0, 0, "k1", value);

        KafkaEventManager.HandleResult r1 = manager.handleRecord(record);
        KafkaEventManager.HandleResult r2 = manager.handleRecord(record);
        assertThat(r1).isEqualTo(KafkaEventManager.HandleResult.RETRY);
        assertThat(r2).isEqualTo(KafkaEventManager.HandleResult.RETRY);
        assertThat(producer.history()).isEmpty();

        KafkaEventManager.HandleResult r3 = manager.handleRecord(record);
        assertThat(r3).isEqualTo(KafkaEventManager.HandleResult.COMMIT);
        assertThat(producer.history()).hasSize(1);
        assertThat(producer.history().get(0).topic()).isEqualTo(TOPIC + "-dlq");
    }

    @Test
    void initializeTopics_createsConsumersByConcurrency() {
        TestableManager manager = new TestableManager(config(3, 3), resolver());
        manager.triggerInitTopics();
        assertThat(manager.createdConsumerCount()).isEqualTo(3);
    }

    /** 可注入并计数 MockConsumer 的测试子类，用于校验按 concurrency 创建消费者。 */
    static final class TestableManager extends KafkaEventManager {
        private final AtomicInteger created = new AtomicInteger(0);

        TestableManager(KafkaConfig config, ITopicResolver resolver) {
            super(KafkaEventManager.builder(config, resolver));
        }

        void triggerInitTopics() {
            initTopics();
        }

        int createdConsumerCount() {
            return created.get();
        }

        @Override
        protected Consumer<String, byte[]> buildConsumer() {
            created.incrementAndGet();
            return new MockConsumer<String, byte[]>(OffsetResetStrategy.EARLIEST);
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
