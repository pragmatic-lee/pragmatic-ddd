package io.pragmatic.ddd.base.id;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IdType 类型枚举测试：确认 LONG/STRING 两类标识类型完备且命名稳定（供注册中心切换）。
  * @author wizard-lee
 */
class IdTypeTest {

    @Test
    void idTypes_coverLongAndString() {
        assertThat(IdType.values()).containsExactly(IdType.LONG, IdType.STRING);
    }

    @Test
    void idTypeNames_matchConvention() {
        assertThat(IdType.LONG.name()).isEqualTo("LONG");
        assertThat(IdType.STRING.name()).isEqualTo("STRING");
    }
}
