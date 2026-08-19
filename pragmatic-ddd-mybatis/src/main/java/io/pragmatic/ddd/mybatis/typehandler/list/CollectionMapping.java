package io.pragmatic.ddd.mybatis.typehandler.list;

import java.util.Objects;

/**
 * 单个集合字段的映射声明：持有字段、元素类型、列标签与可选转换钩子。
 *
 * @author wizard-lee
 */
public final class CollectionMapping {

    private final Class<?> entityClass;
    private final String field;
    private final Class<?> elementType;
    private final String columnLabel;
    private final ElementConverter converter;

    private CollectionMapping(Builder b) {
        this.entityClass = Objects.requireNonNull(b.entityClass, "entityClass");
        this.field = Objects.requireNonNull(b.field, "field");
        this.elementType = Objects.requireNonNull(b.elementType, "elementType");
        // columnLabel 缺省由 table + field 推导（表作用域隔离约定）
        this.columnLabel = b.columnLabel != null ? b.columnLabel : SqlAlias.of(b.table, b.field);
        this.converter = b.converter != null ? b.converter : ElementConverter.IDENTITY;
    }

    public static Builder of(Class<?> entityClass, String field, Class<?> elementType) {
        return new Builder(entityClass, field, elementType);
    }

    public Class<?> entityClass() {
        return entityClass;
    }

    public String field() {
        return field;
    }

    public Class<?> elementType() {
        return elementType;
    }

    /** 结果集列标签（= MyBatis 传入的 column 参数），运行期查表键。 */
    public String columnLabel() {
        return columnLabel;
    }

    /** 运行期查表键（= 列标签）。 */
    public String lookupKey() {
        return columnLabel;
    }

    public ElementConverter converter() {
        return converter;
    }

    @Override
    public String toString() {
        return entityClass.getSimpleName() + "." + field + " -> "
                + elementType.getSimpleName() + " @label=" + columnLabel;
    }

    public static final class Builder {
        private final Class<?> entityClass;
        private final String field;
        private final Class<?> elementType;
        private String table;
        private String columnLabel;
        private ElementConverter converter;

        private Builder(Class<?> entityClass, String field, Class<?> elementType) {
            this.entityClass = entityClass;
            this.field = field;
            this.elementType = elementType;
        }

        /** 可选表名/表别名，用于自动生成 table_field 隔离标签。 */
        public Builder table(String t) {
            this.table = t;
            return this;
        }

        /** 显式结果集列标签（与 SQL AS 别名对齐）；与 {@link #table} 二选一。 */
        public Builder columnLabel(String l) {
            this.columnLabel = l;
            return this;
        }

        /** 可选元素级类型转换钩子。 */
        public Builder converter(ElementConverter c) {
            this.converter = c;
            return this;
        }

        public CollectionMapping build() {
            return new CollectionMapping(this);
        }
    }
}
