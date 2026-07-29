package io.pragmatic.ddd.base;

import io.pragmatic.ddd.base.fixture.SampleMessages;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 对应设计文档阶段 6.4：BrokenRuleObject（违规收集器）单元测试。
 * 保留原有用例并迁 JUnit5 + AssertJ，补充新增用例。
 */
class BrokenRuleObjectTest {

    static class SampleEntity extends BrokenRuleObject {
        @Override
        protected BrokenRuleRegistry brokenRuleRegistry() {
            return SampleMessages.INSTANCE;
        }
    }

    @Test
    void addBrokenRule_messageCode() {
        SampleEntity entity = new SampleEntity();
        entity.addBrokenRule(SampleMessages.NAME_ERROR);
        assertThat(entity.getBrokenRules()).hasSize(1);
        assertThat(entity.getBrokenRules().get(0).getName()).isEqualTo("NAME_ERROR");
        assertThat(entity.getBrokenRules().get(0).getDescription()).isEqualTo("名称:%s 不能为空");
    }

    @Test
    void addParamBrokenRule_format_true() {
        SampleEntity entity = new SampleEntity();
        Object[] params = new Object[]{"张三"};
        entity.addParamBrokenRule(SampleMessages.NAME_ERROR, params, true);
        assertThat(entity.getBrokenRules().get(0).getDescription()).isEqualTo("名称:张三 不能为空");
        assertThat(entity.getBrokenRules().get(0).getExtraData()).containsExactly(params);
    }

    @Test
    void addParamBrokenRule_format_false() {
        SampleEntity entity = new SampleEntity();
        Object[] params = new Object[]{"张三"};
        entity.addParamBrokenRule(SampleMessages.NAME_ERROR, params, false);
        assertThat(entity.getBrokenRules().get(0).getDescription()).isEqualTo("名称:%s 不能为空");
    }

    @Test
    void exceptionCause_passes_extraData() {
        SampleEntity entity = new SampleEntity();
        Object[] params = new Object[]{"张三"};
        entity.addParamBrokenRule(SampleMessages.NAME_ERROR, params, true);
        BrokenRuleException ex = entity.exceptionCause();
        assertThat(ex).isNotNull();
        assertThat(ex.getCode()).isEqualTo("NAME_ERROR");
        assertThat(ex.getMessage()).isEqualTo("名称:张三 不能为空");
        assertThat(ex.getSource()).isSameAs(entity);
    }

    @Test
    void aggregateExceptionCause_size_matches() {
        SampleEntity entity = new SampleEntity();
        entity.addBrokenRule(SampleMessages.NAME_ERROR);
        entity.addBrokenRule(SampleMessages.AGE_ERROR);
        BrokenRuleAggregateException ex = entity.aggregateExceptionCause();
        assertThat(ex).isNotNull();
        assertThat(ex.getExceptions()).hasSize(2);
    }

    @Test
    void clearBrokenRules_exceptionCause_null() {
        SampleEntity entity = new SampleEntity();
        entity.addBrokenRule(SampleMessages.NAME_ERROR);
        entity.clearBrokenRules();
        assertThat(entity.exceptionCause()).isNull();
    }

    @Test
    void getBrokenRules_immutable() {
        SampleEntity entity = new SampleEntity();
        entity.addBrokenRule(SampleMessages.NAME_ERROR);
        assertThatThrownBy(() -> entity.getBrokenRules().add(new BrokenRule("X", "x")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // ===================== 新增用例 =====================

    @Test
    void throwBrokenRuleException_whenNoViolation_doesNotThrow() {
        SampleEntity entity = new SampleEntity();
        assertThatCode(entity::throwBrokenRuleException).doesNotThrowAnyException();
    }

    @Test
    void throwBrokenRuleException_whenViolation_throwsFirst() {
        SampleEntity entity = new SampleEntity();
        entity.addBrokenRule(SampleMessages.NAME_ERROR);
        assertThatThrownBy(entity::throwBrokenRuleException)
                .isInstanceOf(BrokenRuleException.class)
                .satisfies(ex -> assertThat(((BrokenRuleException) ex).getCode()).isEqualTo("NAME_ERROR"));
    }

    @Test
    void throwBrokenRuleAggregateException_multiple() {
        SampleEntity entity = new SampleEntity();
        entity.addBrokenRule(SampleMessages.NAME_ERROR);
        entity.addBrokenRule(SampleMessages.AGE_ERROR);
        assertThatThrownBy(entity::throwBrokenRuleAggregateException)
                .isInstanceOf(BrokenRuleAggregateException.class)
                .satisfies(ex -> {
                    BrokenRuleAggregateException agg = (BrokenRuleAggregateException) ex;
                    assertThat(agg.getExceptions()).hasSize(2);
                    assertThat(agg.getExceptions().get(0).getCode()).isEqualTo("NAME_ERROR");
                    assertThat(agg.getExceptions().get(1).getCode()).isEqualTo("AGE_ERROR");
                });
    }

    @Test
    void setSource_passedToAggregateException() {
        SampleEntity entity = new SampleEntity();
        Object source = new Object();
        entity.setSource(source);
        entity.addBrokenRule(SampleMessages.NAME_ERROR);
        assertThat(entity.aggregateExceptionCause().getSource()).isSameAs(source);
    }
}
