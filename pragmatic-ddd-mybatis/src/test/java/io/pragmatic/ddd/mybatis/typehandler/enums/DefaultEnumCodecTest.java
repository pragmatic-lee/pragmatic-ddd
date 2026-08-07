package io.pragmatic.ddd.mybatis.typehandler.enums;

import io.pragmatic.ddd.base.IEnumValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * DefaultEnumCodec 编解码纯单测：枚举 <-> 库存标量。
 *
 * @author wizard-lee
 */
@DisplayName("DefaultEnumCodec 编解码")
class DefaultEnumCodecTest {

    enum StatusEnum implements IEnumValue<Integer, StatusEnum> {
        NORMAL(1, "正常"),
        DISABLED(2, "禁用"),
        PENDING(3, "待处理");

        private final Integer code;
        private final String label;

        StatusEnum(Integer code, String label) {
            this.code = code;
            this.label = label;
        }

        @Override
        public Integer getValue() {
            return code;
        }

        @Override
        public String getName() {
            return label;
        }
    }

    private final DefaultEnumCodec codec = new DefaultEnumCodec();

    @Test
    @DisplayName("toCode 返回枚举值本身")
    void toCodeReturnsEnumValue() {
        assertThat(codec.toCode(StatusEnum.PENDING)).isEqualTo(3);
        assertThat(codec.toCode(StatusEnum.NORMAL)).isEqualTo(1);
    }
}
