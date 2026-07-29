package io.pragmatic.ddd.base;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 对应设计文档阶段 6.14：IEnumValue 接口 default 方法契约测试。
 */
class IEnumValueTest {

    enum Status implements IEnumValue<String, Status> {
        ACTIVE("active", "启用"),
        INACTIVE("inactive", "停用");

        private final String value;
        private final String name;

        Status(String value, String name) {
            this.value = value;
            this.name = name;
        }

        @Override
        public String getValue() {
            return value;
        }

        @Override
        public String getName() {
            return name;
        }
    }

    @Test
    void enumContract_valueAndName() {
        assertThat(Status.ACTIVE.getValue()).isEqualTo("active");
        assertThat(Status.ACTIVE.getName()).isEqualTo("启用");
        assertThat(Status.INACTIVE.getValue()).isEqualTo("inactive");
    }

    @Test
    void getDesc_defaultEqualsName() {
        // 未覆写 getDesc() → 缺省实现返回 getName()
        assertThat(Status.ACTIVE.getDesc()).isEqualTo("启用");
        assertThat(Status.INACTIVE.getDesc()).isEqualTo("停用");
    }
}
