package io.pragmatic.ddd.rules;

import io.pragmatic.ddd.base.IRule;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link BaseRuleValidator} 抽象适配类的测试。
 *
 * @author wizard-lee
 */
class BaseRuleValidatorTest {

    static class EvenValidator extends BaseRuleValidator<Integer> {
        @Override
        protected boolean validate(Integer model) {
            return model % 2 == 0;
        }
    }

    @Test
    void rule_delegatesToValidate() {
        EvenValidator validator = new EvenValidator();
        IRule<Integer> rule = validator.rule();
        assertThat(rule.satisfiesRule(4)).isTrue();
        assertThat(rule.satisfiesRule(3)).isFalse();
    }

    @Test
    void ruleCondition_alwaysActive() {
        EvenValidator validator = new EvenValidator();
        IActiveRuleCondition<Integer> condition = validator.ruleCondition();
        assertThat(condition.status(123)).isEqualTo(ActiveStatus.ACTIVE);
        assertThat(condition.status(null)).isEqualTo(ActiveStatus.ACTIVE);
    }
}
