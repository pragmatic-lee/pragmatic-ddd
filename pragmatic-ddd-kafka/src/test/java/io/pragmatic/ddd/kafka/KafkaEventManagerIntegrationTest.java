package io.pragmatic.ddd.kafka;

import io.pragmatic.ddd.event.internal.defaults.ConfigurableTopicResolver;
import io.pragmatic.ddd.event.internal.model.SubscribeData;
import io.pragmatic.ddd.event.spi.ITopicResolver;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 基于真实 Kafka broker 的集成测试，结构与 RocketMQ 的 {@code RocketMqDomainEventManagerTest} 对齐。
 * <p>
 * 通过 {@link KafkaTestSupport#isAvailable()} 探测本地（或容器）Kafka，
 * 不可达时整体跳过，不阻塞无 broker 环境的构建。
 *
 * @author wizard-lee
 */
@Tag("integration")
class KafkaEventManagerIntegrationTest {

    private static final String TOPIC_RT = "pdd_ddd_kafka_rt";
    private static final String TOPIC_ROUTE_A = "pdd_ddd_kafka_route_a";
    private static final String TOPIC_ROUTE_B = "pdd_ddd_kafka_route_b";
    private static final String TOPIC_DLQ = "pdd_ddd_kafka_dlq";
    private static final String TOPIC_DLQ_DLQ = TOPIC_DLQ + "-dlq";

    @BeforeAll
    static void requireBroker() {
        assumeTrue(KafkaTestSupport.isAvailable(),
                "跳过真实 Kafka 集成测试：未检测到本地 Kafka（" + KafkaTestSupport.bootstrapServers()
                        + "），可用 -Dkafka.bootstrap-servers=host:port 指定");
        log("已检测到 Kafka broker: %s", KafkaTestSupport.bootstrapServers());
    }

    private static void log(String format, Object... args) {
        String msg = String.format(format, args);
        System.out.printf("[KafkaIT][%s] %s%n",
                Thread.currentThread().getName(), msg);
    }

    @Test
    void publish_event_consumerReceives_realBroker() throws Exception {
        String topic = TOPIC_RT;
        log("用例[consumerReceives] 开始，准备建主题 topic=%s", topic);
        KafkaTestSupport.createTopic(topic);

        CountDownLatch latch = new CountDownLatch(1);
        List<String> received = Collections.synchronizedList(new java.util.ArrayList<>());

        KafkaEventManager manager = KafkaTestSupport.createManager(topic, "g-rt");
        manager.registerSubscriber("test1", MyDomainEvent.class, e -> {
            log("消费者[test1] 收到事件 name=%s entityId=%s", e.getName(), e.getEntityId());
            received.add(e.getName());
            latch.countDown();
        });
        manager.start();
        log("用例[consumerReceives] manager 已启动，准备发布事件");
        try {
            manager.publish(MyDomainEvent.buildEvent("a", "hello"));
            log("用例[consumerReceives] 事件已发布 name=hello");

            boolean done = latch.await(20, TimeUnit.SECONDS);
            log("用例[consumerReceives] 等待消费者结果 done=%s received=%s", done, received);
            assertThat(done).isTrue();
            assertThat(received).containsExactly("hello");
        } finally {
            manager.shutdown();
            log("用例[consumerReceives] manager 已关闭");
        }
    }

    @Test
    void publish_event_routesToCorrectSubscriber_realBroker() throws Exception {
        String topicA = TOPIC_ROUTE_A;
        String topicB = TOPIC_ROUTE_B;
        log("用例[routes] 开始，准备建主题 topicA=%s topicB=%s", topicA, topicB);
        KafkaTestSupport.createTopic(topicA);
        KafkaTestSupport.createTopic(topicB);

        CountDownLatch myLatch = new CountDownLatch(1);
        CountDownLatch shareLatch = new CountDownLatch(1);

        ITopicResolver resolver = ConfigurableTopicResolver.builder()
                .eventTopic("MyDomainEvent", topicA)
                .eventTopic("ShareDomainEvent", topicB)
                .build();

        KafkaEventManager manager = KafkaTestSupport.createManager(resolver, "g-route");
        manager.registerSubscriber("subA", MyDomainEvent.class, e -> {
            log("消费者[subA] 收到 MyDomainEvent name=%s", e.getName());
            myLatch.countDown();
        });
        manager.registerSubscriber("subB", ShareDomainEvent.class, e -> {
            log("消费者[subB] 收到 ShareDomainEvent orderId=%s", e.getOrderId());
            shareLatch.countDown();
        });
        manager.start();
        log("用例[routes] manager 已启动，准备仅发布 MyDomainEvent");
        try {
            manager.publish(MyDomainEvent.buildEvent("a", "a"));
            log("用例[routes] MyDomainEvent 已发布，subA 应收到、subB 应沉默");

            boolean myDone = myLatch.await(20, TimeUnit.SECONDS);
            boolean shareDone = shareLatch.await(3, TimeUnit.SECONDS);
            log("用例[routes] 结果 subA(myLatch)=%s subB(shareLatch)=%s", myDone, shareDone);
            assertThat(myDone).isTrue();
            assertThat(shareDone).isFalse();
        } finally {
            manager.shutdown();
            log("用例[routes] manager 已关闭");
        }
    }

    @Test
    void publish_event_consumerThrows_retriesAndEventuallyDlq_realBroker() throws Exception {
        String topic = TOPIC_DLQ;
        String dlqTopic = TOPIC_DLQ_DLQ;
        log("用例[dlq] 开始，准备建主题 topic=%s dlqTopic=%s", topic, dlqTopic);
        KafkaTestSupport.createTopic(topic);
        KafkaTestSupport.createTopic(dlqTopic);

        Map<String, Integer> attempts = new ConcurrentHashMap<>();
        CountDownLatch latch = new CountDownLatch(3);

        KafkaEventManager manager = KafkaTestSupport.createManager(topic, "g-dlq");
        manager.registerSubscriber("failing", MyDomainEvent.class, e -> {
            int n = attempts.merge(e.getName(), 1, Integer::sum);
            log("消费者[failing] 第 %d 次处理事件 name=%s，即将抛出异常触发重试", n, e.getName());
            latch.countDown();
            throw new RuntimeException("boom");
        });
        manager.start();
        log("用例[dlq] manager 已启动，准备发布会失败的事件");
        try {
            manager.publish(MyDomainEvent.buildEvent("a", "a"));
            log("用例[dlq] 事件已发布 name=a，等待重试达到 3 次");

            boolean done = latch.await(30, TimeUnit.SECONDS);
            log("用例[dlq] 重试等待结果 done=%s attempts=%s", done, attempts);
            assertThat(done).isTrue();
            assertThat(attempts.get("a")).isGreaterThanOrEqualTo(3);

            verifyDlqReceives(dlqTopic, "a");
        } finally {
            manager.shutdown();
            log("用例[dlq] manager 已关闭");
        }
    }

    private void verifyDlqReceives(String dlqTopic, String expectedName) {
        boolean found = false;
        try (KafkaConsumer<String, byte[]> consumer = KafkaTestSupport.createRawConsumer("g-dlq-verify")) {
            consumer.subscribe(Collections.singletonList(dlqTopic));
            log("用例[dlq] 开始轮询死信队列 dlqTopic=%s", dlqTopic);
            long deadline = System.currentTimeMillis() + 20000;
            while (System.currentTimeMillis() < deadline) {
                for (ConsumerRecord<String, byte[]> record : consumer.poll(Duration.ofSeconds(2))) {
                    MyDomainEvent event = readEvent(record.value());
                    log("用例[dlq] 死信队列收到一条记录 name=%s", event.getName());
                    if (expectedName.equals(event.getName())) {
                        found = true;
                        break;
                    }
                }
                if (found) {
                    break;
                }
            }
        }
        log("用例[dlq] 死信队列校验结果 found=%s", found);
        assertThat(found).as("死信队列 %s 应收到事件 %s", dlqTopic, expectedName).isTrue();
    }

    private MyDomainEvent readEvent(byte[] value) {
        String json = new String(value, StandardCharsets.UTF_8);
        SubscribeData subscribeData = new KafkaEventSerializer().deserialize(json, SubscribeData.class);
        return new KafkaEventSerializer().deserialize(subscribeData.getEventData(), MyDomainEvent.class);
    }
}
