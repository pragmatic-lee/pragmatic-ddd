package io.pragmatic.ddd.event.internal.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 MQ 消息投递数据载体的字段存取。
 *
 * @author wizard-lee
 */
class SubscribeDataTest {

    @Test
    void allArgsConstructor_holdsValues() {
        SubscribeData data = new SubscribeData("sub-a", "payload", "RealEvent", true, DeliveryPolicy.DELAYED);
        assertThat(data.getName()).isEqualTo("sub-a");
        assertThat(data.getEventData()).isEqualTo("payload");
        assertThat(data.getRealEventName()).isEqualTo("RealEvent");
        assertThat(data.getOnlyThis()).isTrue();
        assertThat(data.getDeliveryPolicy()).isEqualTo(DeliveryPolicy.DELAYED);
    }

    @Test
    void setters_updateValues() {
        SubscribeData data = new SubscribeData();
        data.setName("sub-b");
        data.setEventData("p2");
        data.setRealEventName("E2");
        data.setOnlyThis(false);
        data.setDeliveryPolicy(DeliveryPolicy.IMMEDIATE);
        assertThat(data.getName()).isEqualTo("sub-b");
        assertThat(data.getEventData()).isEqualTo("p2");
        assertThat(data.getRealEventName()).isEqualTo("E2");
        assertThat(data.getOnlyThis()).isFalse();
        assertThat(data.getDeliveryPolicy()).isEqualTo(DeliveryPolicy.IMMEDIATE);
    }
}
