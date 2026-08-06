package io.pragmatic.ddd.broadcast;

import io.pragmatic.ddd.event.BaseDomainEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AggregateMessageEnvelopeTest {

    private static final class SampleEvent extends BaseDomainEvent {
        SampleEvent(String entityId) {
            super(entityId);
            this.operationCode = "PAID";
            this.version = 3L;
        }
    }

    private static final class SampleEnvelope extends AggregateMessageEnvelope<String> {
        SampleEnvelope(String aggregateType, BaseDomainEvent source, String payload) {
            super(aggregateType, source, payload);
        }
    }

    @Test
    void metadataShouldBeFilledFromSourceEvent() {
        SampleEvent event = new SampleEvent("order-1");
        SampleEnvelope envelope = new SampleEnvelope("Order", event, "payload-body");

        assertThat(envelope.getAggregateId()).isEqualTo("order-1");
        assertThat(envelope.getVersion()).isEqualTo(3L);
        assertThat(envelope.getCauseOperation()).isEqualTo("PAID");
        assertThat(envelope.getOccurredOn()).isInstanceOf(Instant.class);
        assertThat(envelope.getSourceEventId()).isEqualTo(event.getEventId());
        assertThat(envelope.getAggregateType()).isEqualTo("Order");
        assertThat(envelope.getPayload()).isEqualTo("payload-body");
    }

    @Test
    void schemaVersionShouldBeOne() {
        SampleEvent event = new SampleEvent("order-1");
        SampleEnvelope envelope = new SampleEnvelope("Order", event, "p");

        assertThat(envelope.getSchemaVersion()).isEqualTo(1);
    }

    @Test
    void messageIdShouldBeGeneratedAndUnique() {
        SampleEvent event = new SampleEvent("order-1");
        SampleEnvelope first = new SampleEnvelope("Order", event, "p");
        SampleEnvelope second = new SampleEnvelope("Order", event, "p");

        assertThat(first.getMessageId()).isNotNull().isNotEmpty();
        assertThat(first.getMessageId()).isNotEqualTo(second.getMessageId());
    }
}
