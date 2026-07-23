package io.pragmatic.ddd.mybatis.typehandler.list;

/**
 * 表作用域隔离标签生成器：把 (table, field) 规整为结果集列标签 {@code table_field}。
 *
 * <p>多表同名列冲突的根因是"结果集出现两个同名 label"，MyBatis 无法区分。
 * 隔离方案 = SQL 中对每列 {@code AS} 出表作用域别名，本类给出与配置一致的标签，
 * 使"SQL 别名"与"handler 查表键"严格对齐（见 {@link CollectionMapping} 与 {@link ListTypeHandler}）。
 *
 * <p>示例：ORDER 表与 PRODUCT 表都有 {@code tags} 列，分别标注
 * {@code o.tags AS order_tags}、{@code p.tags AS product_tags}，
 * 配置 table="order"/"product" 即自动得到 {@code order_tags}/{@code product_tags}。
 */
public final class SqlAlias {

    private SqlAlias() {
    }

    /** 由 (表名/表别名, 字段) 生成隔离标签；table 为空则回退为 field。 */
    public static String of(String table, String field) {
        if (table == null || table.isBlank()) {
            return field;
        }
        return table.trim() + "_" + field;
    }

    /** 便捷：由实体类简单名 + 字段生成（约定表别名 = 实体类简单名首字母小写）。 */
    public static String of(Class<?> entityClass, String field) {
        return of(toTableAlias(entityClass), field);
    }

    private static String toTableAlias(Class<?> entityClass) {
        String n = entityClass.getSimpleName();
        if (n.isEmpty()) {
            return n;
        }
        return Character.toLowerCase(n.charAt(0)) + n.substring(1);
    }
}
