package io.pragmatic.ddd.base;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 对应设计文档阶段 6.5：异常继承层次契约测试，保障统一 catch 能力。
 */
class ExceptionHierarchyTest {

    @Test
    void inheritanceContract_unifiedCatchable() {
        BrokenRuleException ex = new BrokenRuleException("CODE", "msg");
        assertThat(ex)
                .isInstanceOf(RuleException.class)
                .isInstanceOf(PragmaticException.class)
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void brokenRuleException_twoArgs_sourceNull() {
        BrokenRuleException ex = new BrokenRuleException("CODE", "msg");
        assertThat(ex.getCode()).isEqualTo("CODE");
        assertThat(ex.getMessage()).isEqualTo("msg");
        assertThat(ex.getSource()).isNull();
    }

    @Test
    void brokenRuleException_threeArgs_sourcePreserved() {
        Object source = new Object();
        BrokenRuleException ex = new BrokenRuleException("CODE", "msg", source);
        assertThat(ex.getSource()).isSameAs(source);
    }

    @Test
    void brokenRuleAggregateException_exceptionsOrdered_andDefaultMessage() {
        BrokenRuleException e1 = new BrokenRuleException("C1", "m1");
        BrokenRuleException e2 = new BrokenRuleException("C2", "m2");
        BrokenRuleAggregateException ex = new BrokenRuleAggregateException(List.of(e1, e2));
        assertThat(ex.getExceptions()).containsExactly(e1, e2);
        assertThat(ex.getMessage()).isEmpty();
    }

    @Test
    void brokenRuleAggregateException_getSource_firstChildOrNull() {
        BrokenRuleException e1 = new BrokenRuleException("C1", "m1", "src1");
        BrokenRuleException e2 = new BrokenRuleException("C2", "m2", "src2");
        BrokenRuleAggregateException ex = new BrokenRuleAggregateException(List.of(e1, e2));
        assertThat(ex.getSource()).isEqualTo("src1");

        BrokenRuleAggregateException empty =
                new BrokenRuleAggregateException(List.of());
        assertThat(empty.getSource()).isNull();
    }
}
