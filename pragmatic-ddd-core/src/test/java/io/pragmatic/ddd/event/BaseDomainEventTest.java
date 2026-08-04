package io.pragmatic.ddd.event;

import io.pragmatic.ddd.event.support.TestDomainEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证领域事件基类的字段存取默认行为。
 *
 * @author wizard-lee
 */
class BaseDomainEventTest {

    @Test
    void constructor_setsEntityIdAndGeneratesEventId() {
        TestDomainEvent event = new TestDomainEvent("agg-42");
        assertThat(event.getEntityId()).isEqualTo("agg-42");
        assertThat(event.getEventId()).isNotBlank();
        assertThat(event.getOccurredOn()).isNotNull();
    }

    @Test
    void defaultOperationCodeAndVersion_areZero() {
        TestDomainEvent event = new TestDomainEvent("agg-1");
        assertThat(event.getOperationCode()).isNull();
        assertThat(event.getVersion()).isZero();
    }

    @Test
    void withOperationCode_and_withVersion_updateFields() {
        TestDomainEvent event = new TestDomainEvent("agg-1")
                .withOperationCode("OP-TEST")
                .withVersion(7L);
        assertThat(event.getOperationCode()).isEqualTo("OP-TEST");
        assertThat(event.getVersion()).isEqualTo(7L);
    }

    @Test
    void getAggregateId_defaultsToEntityId() {
        TestDomainEvent event = new TestDomainEvent("agg-9");
        assertThat(event.getAggregateId()).isEqualTo("agg-9");
    }

    @Test
    void occurredOn_reflectsConstructionTime() {
        Instant now = Instant.now();
        TestDomainEvent event = new TestDomainEvent("agg-1");
        assertThat(event.getOccurredOn()).isAfterOrEqualTo(now.minusSeconds(1));
    }
}
