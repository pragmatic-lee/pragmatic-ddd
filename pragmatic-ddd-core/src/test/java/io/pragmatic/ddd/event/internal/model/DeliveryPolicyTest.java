package io.pragmatic.ddd.event.internal.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证事件投递策略枚举的取值与解析。
 *
 * @author wizard-lee
 */
class DeliveryPolicyTest {

    @Test
    void values_containsDelayedAndImmediate() {
        DeliveryPolicy[] values = DeliveryPolicy.values();
        assertThat(values).containsExactlyInAnyOrder(DeliveryPolicy.DELAYED, DeliveryPolicy.IMMEDIATE);
    }

    @Test
    void valueOf_resolvesByName() {
        assertThat(DeliveryPolicy.valueOf("DELAYED")).isEqualTo(DeliveryPolicy.DELAYED);
        assertThat(DeliveryPolicy.valueOf("IMMEDIATE")).isEqualTo(DeliveryPolicy.IMMEDIATE);
    }
}
