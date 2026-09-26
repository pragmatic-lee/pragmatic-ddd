package io.pragmatic.ddd.kafka;

import io.pragmatic.ddd.event.internal.model.SubscribeData;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * KafkaEventSerializer 序列化往返测试。
 *
 * @author wizard-lee
 */
class KafkaEventSerializerTest {

    private final KafkaEventSerializer serializer = new KafkaEventSerializer();

    @Test
    void serializeThenDeserialize_keepsFields() {
        SubscribeData origin = new SubscribeData(
                "subscriber-a",
                "{\"id\":1}",
                "OrderPaidEvent",
                false,
                null);

        String json = serializer.serialize(origin);
        SubscribeData back = serializer.deserialize(json, SubscribeData.class);

        assertThat(back.getName()).isEqualTo(origin.getName());
        assertThat(back.getEventData()).isEqualTo(origin.getEventData());
        assertThat(back.getRealEventName()).isEqualTo(origin.getRealEventName());
        assertThat(back.getOnlyThis()).isEqualTo(origin.getOnlyThis());
    }
}
