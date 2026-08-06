package io.pragmatic.ddd.broadcast;

import io.pragmatic.ddd.event.BaseDomainEvent;
import io.pragmatic.ddd.event.IDomainEvent;
import io.pragmatic.ddd.event.spi.IEventSerializer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AbstractBroadcastSubscriberTest {

    private static final class SampleEvent extends BaseDomainEvent {
        SampleEvent(String entityId) {
            super(entityId);
            this.operationCode = "PAID";
            this.version = 1L;
        }
    }

    private static final class SamplePayload {
        private final String orderId;

        SamplePayload(String orderId) {
            this.orderId = orderId;
        }
    }

    private static final class SampleEnvelope extends AggregateMessageEnvelope<SamplePayload> {
        SampleEnvelope(String aggregateType, IDomainEvent source, SamplePayload payload) {
            super(aggregateType, source, payload);
        }
    }

    private static class SampleSubscriber extends AbstractBroadcastSubscriber<SampleEvent, SamplePayload> {
        private final RecordingMessenger messenger;
        private final RecordingSerializer serializer;

        SampleSubscriber() {
            this(new RecordingMessenger(), new RecordingSerializer());
        }

        private SampleSubscriber(RecordingMessenger messenger, RecordingSerializer serializer) {
            super(messenger, serializer, "topic-order-external", "broadcast");
            this.messenger = messenger;
            this.serializer = serializer;
        }

        @Override
        public Class<SampleEvent> subscribedToEventType() {
            return SampleEvent.class;
        }

        @Override
        protected SamplePayload buildPayload(SampleEvent event) {
            return new SamplePayload(event.getAggregateId());
        }

        @Override
        protected AggregateMessageEnvelope<SamplePayload> wrap(SampleEvent event, SamplePayload payload) {
            return new SampleEnvelope("Order", event, payload);
        }
    }

    private static final class RecordingMessenger implements IBroadcastMessenger {
        String topic;
        String senderCode;
        String serializedEnvelope;

        @Override
        public void send(String topic, String senderCode, String serializedEnvelope) {
            this.topic = topic;
            this.senderCode = senderCode;
            this.serializedEnvelope = serializedEnvelope;
        }
    }

    private static final class RecordingSerializer implements IEventSerializer {
        @Override
        public <T> String serialize(T event) {
            AggregateMessageEnvelope<?> env = (AggregateMessageEnvelope<?>) event;
            return "env:" + env.getAggregateId() + ":" + ((SamplePayload) env.getPayload()).orderId;
        }

        @Override
        public <T> T deserialize(String data, Class<T> eventType) {
            throw new UnsupportedOperationException();
        }
    }

    @Test
    void handleEventShouldSendSerializedEnvelopeViaMessenger() {
        SampleSubscriber subscriber = new SampleSubscriber();

        subscriber.handleEvent(new SampleEvent("order-1"));

        assertThat(subscriber.messenger.topic).isEqualTo("topic-order-external");
        assertThat(subscriber.messenger.senderCode).isEqualTo("broadcast");
        assertThat(subscriber.messenger.serializedEnvelope).isEqualTo("env:order-1:order-1");
    }

    @Test
    void subscriberShouldExposeSubscribedEventType() {
        SampleSubscriber subscriber = new SampleSubscriber();

        assertThat(subscriber.subscribedToEventType()).isEqualTo(SampleEvent.class);
    }
}
