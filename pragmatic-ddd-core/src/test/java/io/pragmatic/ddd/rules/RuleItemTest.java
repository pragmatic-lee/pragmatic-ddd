package io.pragmatic.ddd.rules;

import io.pragmatic.ddd.base.ICheckRule;
import io.pragmatic.ddd.base.MessageCode;
import io.pragmatic.ddd.base.RuleCheckResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link RuleItem} 构造与取值测试。
 *
 * @author wizard-lee
 */
class RuleItemTest {

    static final MessageCode CODE = MessageCode.of("C1", "desc");
    static final IActiveRuleCondition<Object> CONDITION = model -> ActiveStatus.ACTIVE;

    static class SimpleRule implements ICheckRule<Object> {
        @Override
        public RuleCheckResult check(Object model) {
            return RuleCheckResult.pass();
        }
    }

    @Test
    void constructWithICheckRule_exposesRuleAndCondition() {
        ICheckRule<Object> rule = new SimpleRule();
        RuleItem<Object> item = new RuleItem<>(rule, CODE, CONDITION);
        assertThat(item.getRule()).isSameAs(rule);
        assertThat(item.getCondition()).isSameAs(CONDITION);
        assertThat(item.getMessageCode()).isEqualTo(CODE);
    }
}
