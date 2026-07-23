package io.pragmatic.ddd.mybatis.typehandler.list;

import io.pragmatic.ddd.base.IEnumValue;
import io.pragmatic.ddd.mybatis.typehandler.enums.EnumRule;
import io.pragmatic.ddd.mybatis.typehandler.enums.EnumValueResolver;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CollectionElementTypeConfigTest {

    enum Status implements IEnumValue<Integer, Status> {
        A(1, "a"), B(2, "b");
        private final int v;
        private final String n;

        Status(int v, String n) {
            this.v = v;
            this.n = n;
        }

        @Override
        public Integer getValue() {
            return v;
        }

        @Override
        public String getName() {
            return n;
        }
    }

    static class Order {
    }

    static class Product {
    }

    @Test
    void multi_table_same_field_different_type_isolated_by_label() {
        EnumValueResolver resolver = new EnumValueResolver();
        CollectionElementTypeConfig cfg = CollectionElementTypeConfig.from(List.of(
                CollectionMapping.of(Order.class, "tags", String.class).table("o").build(),
                CollectionMapping.of(Product.class, "tags", Integer.class).table("p").build(),
                CollectionMapping.of(Order.class, "steps", Status.class).build()
        ), resolver);

        Map<String, java.lang.reflect.Type> types = cfg.columnListTypes();
        assertThat(types).containsKeys("o_tags", "p_tags", "steps");
        // 同字段 tags 在两表下被隔离为不同类型，互不覆盖
        assertThat(types.get("o_tags")).isInstanceOf(java.lang.reflect.ParameterizedType.class);
        assertThat(types.get("p_tags")).isInstanceOf(java.lang.reflect.ParameterizedType.class);
        assertThat(((java.lang.reflect.ParameterizedType) types.get("o_tags")).getRawType())
                .isEqualTo(List.class);
        assertThat(types.get("o_tags").getTypeName()).contains("List");
        assertThat(resolver.ruleOf(Status.class)).isEqualTo(EnumRule.CODE); // 枚举已自动注册
    }

    @Test
    void same_label_different_type_fails_fast() {
        EnumValueResolver resolver = new EnumValueResolver();
        assertThatThrownBy(() -> CollectionElementTypeConfig.from(List.of(
                CollectionMapping.of(Order.class, "tags", String.class).columnLabel("tags").build(),
                CollectionMapping.of(Product.class, "tags", Integer.class).columnLabel("tags").build()
        ), resolver)).isInstanceOf(IllegalStateException.class);
    }
}
