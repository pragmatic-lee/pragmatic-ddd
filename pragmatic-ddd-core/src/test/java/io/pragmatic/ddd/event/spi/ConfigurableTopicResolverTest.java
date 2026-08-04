package io.pragmatic.ddd.event.spi;

import io.pragmatic.ddd.event.internal.defaults.ConfigurableTopicResolver;
import io.pragmatic.ddd.event.support.TestDomainEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证三层次可配置 Topic 解析器的优先级与聚合行为。
 *
 * @author wizard-lee
 */
class ConfigurableTopicResolverTest {

    @Test
    void onlyGlobalDefault_resolvesToGlobal() {
        ConfigurableTopicResolver resolver = ConfigurableTopicResolver.builder()
                .globalDefaultTopic("global-topic")
                .build();
        assertThat(resolver.resolveForType(TestDomainEvent.class)).isEqualTo("global-topic");
        assertThat(resolver.resolveForEvent(new TestDomainEvent(), "sub-a")).isEqualTo("global-topic");
    }

    @Test
    void eventLevel_overridesGlobal() {
        ConfigurableTopicResolver resolver = ConfigurableTopicResolver.builder()
                .globalDefaultTopic("global-topic")
                .eventTopic("TestDomainEvent", "event-topic")
                .build();
        assertThat(resolver.resolveForType(TestDomainEvent.class)).isEqualTo("event-topic");
    }

    @Test
    void subscriberLevel_highestPriority() {
        ConfigurableTopicResolver resolver = ConfigurableTopicResolver.builder()
                .globalDefaultTopic("global-topic")
                .eventTopic("TestDomainEvent", "event-topic")
                .subscriberTopic("TestDomainEvent", "sub-a", "sub-topic")
                .build();
        assertThat(resolver.resolveForEvent(new TestDomainEvent(), "sub-a")).isEqualTo("sub-topic");
        assertThat(resolver.resolveForEvent(new TestDomainEvent(), "sub-b")).isEqualTo("event-topic");
    }

    @Test
    void getAllTopics_containsAllLevelsWithoutDuplicates() {
        ConfigurableTopicResolver resolver = ConfigurableTopicResolver.builder()
                .globalDefaultTopic("global-topic")
                .eventTopic("TestDomainEvent", "event-topic")
                .subscriberTopic("TestDomainEvent", "sub-a", "event-topic")
                .build();
        assertThat(resolver.getAllTopics()).containsExactlyInAnyOrder("global-topic", "event-topic");
    }

    @Test
    void emptyBuilder_resolvesTypeToNull() {
        ConfigurableTopicResolver resolver = ConfigurableTopicResolver.builder().build();
        assertThat(resolver.resolveForType(TestDomainEvent.class)).isNull();
        assertThat(resolver.getAllTopics()).isEmpty();
    }
}
