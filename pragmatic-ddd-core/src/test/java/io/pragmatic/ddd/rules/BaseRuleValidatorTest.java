package io.pragmatic.ddd.rules;

import io.pragmatic.ddd.base.ICheckRule;
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
        protected boolean validate(Integer model, Integer oldModel) {
            return model % 2 == 0;
        }
    }

    @Test
    void rule_delegatesToValidate() {
        EvenValidator validator = new EvenValidator();
        ICheckRule<Integer> rule = validator.rule();
        assertThat(rule.check(4, null).isSatisfy()).isTrue();
        assertThat(rule.check(3, null).isSatisfy()).isFalse();
    }

    @Test
    void ruleCondition_alwaysActive() {
        EvenValidator validator = new EvenValidator();
        IActiveRuleCondition<Integer> condition = validator.ruleCondition();
        assertThat(condition.status(123, null)).isEqualTo(ActiveStatus.ACTIVE);
        assertThat(condition.status(null, null)).isEqualTo(ActiveStatus.ACTIVE);
    }
}
