package io.pragmatic.ddd.base;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 对应设计文档阶段 6.2：BrokenRule（违规信息载体）单元测试。
 */
class BrokenRuleTest {

    @Test
    void twoArgConstructor_defaultsExtraDataNull() {
        BrokenRule rule = new BrokenRule("NAME_ERROR", "名称不能为空");
        assertThat(rule.getName()).isEqualTo("NAME_ERROR");
        assertThat(rule.getDescription()).isEqualTo("名称不能为空");
        assertThat(rule.getExtraData()).isNull();
    }

    @Test
    void threeArgConstructor_preservesExtraData() {
        Object[] data = new Object[]{"张三", 18};
        BrokenRule rule = new BrokenRule("NAME_ERROR", "名称:%s 不能为空", data);
        assertThat(rule.getName()).isEqualTo("NAME_ERROR");
        assertThat(rule.getDescription()).isEqualTo("名称:%s 不能为空");
        assertThat(rule.getExtraData()).isSameAs(data);
        assertThat(rule.getExtraData()).containsExactly("张三", 18);
    }
}
