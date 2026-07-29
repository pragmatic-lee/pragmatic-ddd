package io.pragmatic.ddd.base;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 对应设计文档阶段 6.1：MessageCode（record 值对象）单元测试。
 */
class MessageCodeTest {

    @Test
    void of_withDescription_accessors() {
        MessageCode code = MessageCode.of("NAME_ERROR", "名称不能为空");
        assertThat(code.localCode()).isEqualTo("NAME_ERROR");
        assertThat(code.description()).isEqualTo("名称不能为空");
        assertThat(code.code()).isEqualTo("NAME_ERROR");
    }

    @Test
    void of_singleArg_descriptionEmpty() {
        MessageCode code = MessageCode.of("AGE_ERROR");
        assertThat(code.localCode()).isEqualTo("AGE_ERROR");
        assertThat(code.description()).isEmpty();
    }

    @Test
    void equals_onlyByLocalCode() {
        MessageCode a = MessageCode.of("X", "desc1");
        MessageCode b = MessageCode.of("X", "desc2");
        MessageCode c = MessageCode.of("Y", "desc1");

        assertThat(a).isEqualTo(b);        // 同 code，异 description → 相等
        assertThat(a).isNotEqualTo(c);     // 异 code → 不等
        assertThat(a).isNotEqualTo(null);  // 与 null 不等
        assertThat(a).isNotEqualTo("X");   // 异类型不等
        assertThat(a).isEqualTo(a);        // 自反
    }

    @Test
    void hashCode_consistentWithEquals() {
        MessageCode a = MessageCode.of("X", "desc1");
        MessageCode b = MessageCode.of("X", "desc2");
        assertThat(a.hashCode()).isEqualTo(b.hashCode());

        Map<MessageCode, String> map = new HashMap<>();
        map.put(a, "v");
        assertThat(map.get(b)).isEqualTo("v");
    }

    @Test
    void code_equalsLocalCode() {
        MessageCode code = MessageCode.of("X", "d");
        assertThat(code.code()).isEqualTo(code.localCode());
    }
}
