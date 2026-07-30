package io.pragmatic.ddd.rules;

import io.pragmatic.ddd.base.IRule;
import io.pragmatic.ddd.base.MessageCode;
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

    static class SimpleRule implements IRule<Object> {
        @Override
        public boolean satisfiesRule(Object model) {
            return true;
        }
    }

    static class SimpleParamRule implements IParamRule<Object> {
        @Override
        public RuleCheckResult isSatisfy(Object model) {
            return RuleCheckResult.pass();
        }
    }

    @Test
    void constructWithIRule_exposesRuleAndNullParamRule() {
        IRule<Object> rule = new SimpleRule();
        RuleItem<Object> item = new RuleItem<>(rule, CODE, CONDITION);
        assertThat(item.getRule()).isSameAs(rule);
        assertThat(item.getParamRule()).isNull();
        assertThat(item.getCondition()).isSameAs(CONDITION);
        assertThat(item.getMessageCode()).isEqualTo(CODE);
    }

    @Test
    void constructWithIParamRule_exposesParamRuleAndNullRule() {
        IParamRule<Object> paramRule = new SimpleParamRule();
        RuleItem<Object> item = new RuleItem<>(paramRule, CODE, CONDITION);
        assertThat(item.getParamRule()).isSameAs(paramRule);
        assertThat(item.getRule()).isNull();
        assertThat(item.getCondition()).isSameAs(CONDITION);
        assertThat(item.getMessageCode()).isEqualTo(CODE);
    }
}
