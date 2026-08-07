package io.pragmatic.ddd.base;

import io.pragmatic.ddd.base.fixture.SampleAggregate;
import io.pragmatic.ddd.base.fixture.SampleMessages;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 对应设计文档阶段 6.8：AggregateRoot 的规则校验委托测试（吸收原 BrokenRuleRefactorTest 实体用例）。
  * @author wizard-lee
 */
class AggregateRootRuleTest {

    static class RecordingRule implements IRule<SampleAggregate> {
        SampleAggregate capturedModel;
        boolean result = true;

        @Override
        public boolean satisfiesRule(SampleAggregate model) {
            this.capturedModel = model;
            return result;
        }
    }

    @Test
    void addBrokenRule_delegates() {
        SampleAggregate agg = new SampleAggregate(SampleMessages.INSTANCE, null);
        agg.addBrokenRule(SampleMessages.NAME_ERROR);
        assertThat(agg.getBrokenRules()).hasSize(1);
        assertThat(agg.getBrokenRules().get(0).getName()).isEqualTo("NAME_ERROR");
        assertThat(agg.getBrokenRules().get(0).getDescription()).isEqualTo("名称:%s 不能为空");
    }

    @Test
    void addParamBrokenRule_format_trueAndFalse() {
        SampleAggregate agg = new SampleAggregate(SampleMessages.INSTANCE, null);
        agg.addParamBrokenRule(SampleMessages.NAME_ERROR, new Object[]{"张三"}, true);
        assertThat(agg.getBrokenRules().get(0).getDescription()).isEqualTo("名称:张三 不能为空");

        SampleAggregate agg2 = new SampleAggregate(SampleMessages.INSTANCE, null);
        agg2.addParamBrokenRule(SampleMessages.NAME_ERROR, new Object[]{"张三"}, false);
        assertThat(agg2.getBrokenRules().get(0).getDescription()).isEqualTo("名称:%s 不能为空");
    }

    @Test
    void satisfiesRule_null_returnsFalse_noNpe() {
        SampleAggregate agg = new SampleAggregate(SampleMessages.INSTANCE, null);
        assertThat(agg.satisfiesRule(null)).isFalse();
    }

    @Test
    void satisfiesRule_delegatesModelAndResult() {
        SampleAggregate agg = new SampleAggregate(SampleMessages.INSTANCE, null);
        RecordingRule rule = new RecordingRule();
        rule.result = false;
        boolean result = agg.satisfiesRule(rule);
        assertThat(result).isFalse();
        assertThat(rule.capturedModel).isSameAs(agg);
    }

    @Test
    void exceptionCause_sourceIsAggregateItself() {
        SampleAggregate agg = new SampleAggregate(SampleMessages.INSTANCE, null);
        agg.addBrokenRule(SampleMessages.NAME_ERROR);
        assertThat(agg.exceptionCause().getSource()).isSameAs(agg);
    }

    @Test
    void clearBrokenRules_reusable() {
        SampleAggregate agg = new SampleAggregate(SampleMessages.INSTANCE, null);
        agg.addBrokenRule(SampleMessages.NAME_ERROR);
        agg.clearBrokenRules();
        assertThat(agg.getBrokenRules()).isEmpty();
        agg.addBrokenRule(SampleMessages.AGE_ERROR);
        assertThat(agg.getBrokenRules()).hasSize(1);
    }
}
