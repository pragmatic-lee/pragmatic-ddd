package io.pragmatic.ddd.config;

import io.pragmatic.ddd.application.outbox.OutboxRelayConfig;
import io.pragmatic.ddd.base.id.IdGeneratorDefinition;
import io.pragmatic.ddd.base.id.IdGeneratorRegistry;
import io.pragmatic.ddd.base.id.IdType;
import io.pragmatic.ddd.event.internal.defaults.ConfigurableTopicResolver;
import io.pragmatic.ddd.event.local.LocalEventManagerConfig;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证核心组件配置（Outbox、ID 生成器、Topic 解析等）的绑定契约。
 *
 * @author wizard-lee
 */
class CoreConfigBindingTest {

    @Test
    void outboxRelayConfigShouldBindFromSource() {
        MapConfigurationSource source = new MapConfigurationSource();
        source.put("outbox.poll-interval", "PT10M");
        source.put("outbox.grace", "PT1M");
        source.put("outbox.batch-size", "50");
        source.put("outbox.max-attempts", "5");

        OutboxRelayConfig config = OutboxRelayConfig.bind(source);

        assertThat(config.pollInterval()).isEqualTo(Duration.ofMinutes(10));
        assertThat(config.grace()).isEqualTo(Duration.ofMinutes(1));
        assertThat(config.batchSize()).isEqualTo(50);
        assertThat(config.maxAttempts()).isEqualTo(5);
    }

    @Test
    void localEventManagerConfigShouldBindFromSource() {
        MapConfigurationSource source = new MapConfigurationSource();
        source.put("event.local.core-pool-size", "16");
        source.put("event.local.max-pool-size", "32");
        source.put("event.local.queue-capacity", "500");
        source.put("event.local.max-retry-times", "7");
        source.put("event.local.retry-delay-ms", "2000");

        LocalEventManagerConfig config = LocalEventManagerConfig.bind(source);

        assertThat(config.corePoolSize()).isEqualTo(16);
        assertThat(config.maxPoolSize()).isEqualTo(32);
        assertThat(config.queueCapacity()).isEqualTo(500);
        assertThat(config.maxRetryTimes()).isEqualTo(7);
        assertThat(config.retryDelayMs()).isEqualTo(2000);
    }

    @Test
    void configurableTopicResolverShouldLoadFromSource() {
        MapConfigurationSource source = new MapConfigurationSource();
        source.put("event.topic.default", "topic-default");
        source.put("event.topic.event.OrderPaidEvent", "topic-order");
        source.put("event.topic.subscriber.OrderPaidEvent#sms", "topic-sms");

        ConfigurableTopicResolver resolver = ConfigurableTopicResolver.fromSource(source);

        assertThat(resolver.getAllTopics()).contains("topic-default", "topic-order", "topic-sms");
        assertThat(resolver.resolveForType(DummyEvent.class)).isEqualTo("topic-default");
    }

    @Test
    void idGeneratorRegistryShouldLoadFromSource() {
        MapConfigurationSource source = new MapConfigurationSource();
        source.put("id.order.start-id", "1");
        source.put("id.order.step", "100");
        source.put("id.order.id-type", "STRING");
        source.put("id.order.format", "ORD-%08d");

        IdGeneratorRegistry registry = new IdGeneratorRegistry();
        registry.loadFrom(source, bizKey -> new io.pragmatic.ddd.base.id.IdSegment(1, 100, 100));

        Object id = registry.nextId("order");
        assertThat(id).isNotNull();
    }

    private static final class DummyEvent implements io.pragmatic.ddd.event.IDomainEvent {
        @Override
        public String getEventId() {
            return "dummy";
        }

        @Override
        public String getEntityId() {
            return "dummy";
        }

        @Override
        public java.time.Instant getOccurredOn() {
            return java.time.Instant.EPOCH;
        }

        @Override
        public String getOperationCode() {
            return "DUMMY";
        }

        @Override
        public long getVersion() {
            return 0;
        }
    }
}
