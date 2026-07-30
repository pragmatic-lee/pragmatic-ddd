package io.pragmatic.ddd.operation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EntityOperation 实体操作描述符测试。
 *
 * @author wizard-lee
 */
class EntityOperationTest {

    @Test
    void of_withDescription_returnsCodeAndDescription() {
        EntityOperation op = EntityOperation.of("submit", "提交");

        assertThat(op.code()).isEqualTo("submit");
        assertThat(op.description()).isEqualTo("提交");
    }

    @Test
    void of_codeOnly_descriptionIsEmpty() {
        EntityOperation op = EntityOperation.of("submit");

        assertThat(op.code()).isEqualTo("submit");
        assertThat(op.description()).isEmpty();
    }

    @Test
    void equals_sameCode_differentDescription_isEqual() {
        EntityOperation left = EntityOperation.of("submit", "提交");
        EntityOperation right = EntityOperation.of("submit", "另一个描述");

        assertThat(left).isEqualTo(right);
    }

    @Test
    void equals_differentCode_isNotEqual() {
        EntityOperation left = EntityOperation.of("submit");
        EntityOperation right = EntityOperation.of("cancel");

        assertThat(left).isNotEqualTo(right);
    }

    @Test
    void hashCode_sameCode_isSame() {
        EntityOperation left = EntityOperation.of("submit", "提交");
        EntityOperation right = EntityOperation.of("submit", "另一个描述");

        assertThat(left).hasSameHashCodeAs(right);
    }
}
