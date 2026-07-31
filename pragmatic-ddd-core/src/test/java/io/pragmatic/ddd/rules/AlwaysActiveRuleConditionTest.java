package io.pragmatic.ddd.rules;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AlwaysActiveRuleCondition} 始终生效的激活条件测试。
 *
 * @author wizard-lee
 */
class AlwaysActiveRuleConditionTest {

    static class Sample {
        private String value;
    }

    @Test
    void status_alwaysActive_forAnyModel() {
        IActiveRuleCondition<Sample> condition = new AlwaysActiveRuleCondition<>();
        Sample sample = new Sample();
        assertThat(condition.status(sample, null)).isEqualTo(ActiveStatus.ACTIVE);
    }

    @Test
    void status_alwaysActive_forNullModel() {
        IActiveRuleCondition<Sample> condition = new AlwaysActiveRuleCondition<>();
        assertThat(condition.status(null, null)).isEqualTo(ActiveStatus.ACTIVE);
    }
}
