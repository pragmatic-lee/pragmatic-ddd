package io.pragmatic.ddd.rules;

import io.pragmatic.ddd.base.MessageCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link IActiveRuleCondition} code 级开关方法（status(MessageCode)）的默认行为与重载调用测试。
 *
 * @author wizard-lee
 */
class IActiveRuleConditionTest {

    static class Sample {
    }

    @Test
    void switchStatus_default_isActive() {
        IActiveRuleCondition<Sample> condition = new AlwaysActiveRuleCondition<>();
        assertThat(condition.switchStatus(MessageCode.of("ANY_CODE"))).isEqualTo(ActiveStatus.ACTIVE);
    }

    @Test
    void switchStatus_default_forNullCode_isActive() {
        IActiveRuleCondition<Sample> condition = new AlwaysActiveRuleCondition<>();
        assertThat(condition.switchStatus((MessageCode) null)).isEqualTo(ActiveStatus.ACTIVE);
    }

    @Test
    void switchStatus_overriddenReturnsInactive() {
        IActiveRuleCondition<Sample> condition = new AlwaysActiveRuleCondition<>() {
            @Override
            public ActiveStatus switchStatus(MessageCode messageCode) {
                return ActiveStatus.INACTIVE;
            }
        };
        assertThat(condition.switchStatus(MessageCode.of("ANY_CODE"))).isEqualTo(ActiveStatus.INACTIVE);
    }
}
