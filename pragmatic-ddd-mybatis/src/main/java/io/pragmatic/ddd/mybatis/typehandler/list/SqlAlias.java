package io.pragmatic.ddd.mybatis.typehandler.list;

/**
 * 表作用域隔离标签生成器：把 (table, field) 规整为结果集列标签 {@code table_field}。
 *
 * @author wizard-lee
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
