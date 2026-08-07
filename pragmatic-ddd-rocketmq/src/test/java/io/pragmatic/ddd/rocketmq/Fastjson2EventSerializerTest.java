package io.pragmatic.ddd.rocketmq;

import io.pragmatic.ddd.event.internal.model.DeliveryPolicy;
import io.pragmatic.ddd.event.internal.model.SubscribeData;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link Fastjson2EventSerializer} 纯单元测试（不依赖真实 RocketMQ）。
 *
 * @author wizard-lee
 */
class Fastjson2EventSerializerTest {

    private final Fastjson2EventSerializer serializer = new Fastjson2EventSerializer();

    @Test
    void serialize_then_deserialize_event_roundTrip() {
        MyDomainEvent event = MyDomainEvent.buildEvent("abc", "abc");

        String json = serializer.serialize(event);
        assertThat(json).isNotBlank();

        MyDomainEvent restored = serializer.deserialize(json, MyDomainEvent.class);
        assertThat(restored.getName()).isEqualTo(event.getName());
        assertThat(restored.getEntityId()).isEqualTo(event.getEntityId());
    }

    @Test
    void serialize_subscribeData_preserves_fields() {
        SubscribeData data = new SubscribeData(
                "sub1", "{\"name\":\"abc\"}", MyDomainEvent.class.getName(), false, DeliveryPolicy.IMMEDIATE);

        String json = serializer.serialize(data);
        SubscribeData restored = serializer.deserialize(json, SubscribeData.class);

        assertThat(restored.getName()).isEqualTo("sub1");
        assertThat(restored.getRealEventName()).isEqualTo(MyDomainEvent.class.getName());
        assertThat(restored.getOnlyThis()).isFalse();
        assertThat(restored.getDeliveryPolicy()).isEqualTo(DeliveryPolicy.IMMEDIATE);
    }

    @Test
    void deserialize_invalid_json_throws() {
        assertThatThrownBy(() -> serializer.deserialize("{not-valid-json", MyDomainEvent.class))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void serialize_null_event_returns_null() {
        assertThat(serializer.serialize(null)).isNull();
    }
}
