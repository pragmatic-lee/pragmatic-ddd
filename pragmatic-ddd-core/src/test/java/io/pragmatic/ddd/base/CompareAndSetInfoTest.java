package io.pragmatic.ddd.base;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 对应设计文档阶段 6.13：CompareAndSetInfo 单元测试。
 */
class CompareAndSetInfoTest {

    @Test
    void constructor_accessors_nullSafe() {
        CompareAndSetInfo<String> info = new CompareAndSetInfo<>(true, "new", "old");
        assertThat(info.isEqual()).isTrue();
        assertThat(info.getNewValue()).isEqualTo("new");
        assertThat(info.getOldValue()).isEqualTo("old");

        CompareAndSetInfo<String> nullInfo = new CompareAndSetInfo<>(false, null, null);
        assertThat(nullInfo.isEqual()).isFalse();
        assertThat(nullInfo.getNewValue()).isNull();
        assertThat(nullInfo.getOldValue()).isNull();
    }
}
