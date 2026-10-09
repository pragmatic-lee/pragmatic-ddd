package io.pragmatic.ddd.application.compensation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 幂等键生成测试：格式 {聚合类型}:{聚合标识}:{需求编码}，标识缺失退化 UNIDENTIFIED。
 *
 * @author wizard-lee
 */
class ActionKeysTest {

    @Test
    void format_aggregateTypeIdentityRequirementCode() {
        assertThat(ActionKeys.of(new TestAggregate(), "reserve-inventory"))
                .isEqualTo("TestAggregate:1:reserve-inventory");
    }

    @Test
    void nullIdentity_degradesToUnidentified() {
        assertThat(ActionKeys.of(new TestAggregate(null), "reserve-inventory"))
                .isEqualTo("TestAggregate:UNIDENTIFIED:reserve-inventory");
    }
}
