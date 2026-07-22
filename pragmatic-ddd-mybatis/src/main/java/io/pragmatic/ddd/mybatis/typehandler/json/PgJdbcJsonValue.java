package io.pragmatic.ddd.mybatis.typehandler.json;

import com.alibaba.fastjson2.JSON;

/**
 * PostgreSQL 适配器：把结构化 JSON 值转成驱动专属的 {@code org.postgresql.util.PGobject}
 * （type=jsonb / json），供 {@code PreparedStatement.setObject} 写入原生 jsonb 列。
 *
 * <p>裸 {@code JSONObject} 在 PostgreSQL 下会被当作 unknown 类型报错，必须包成 {@code PGobject}。
 * 这里通过反射构造 {@code PGobject}，避免对 {@code org.postgresql} 的硬编译依赖；
 * 仅在使用 PostgreSQL 时装配（把 {@link JdbcJsonValue#DEFAULT} 换成 {@code new PgJdbcJsonValue()} 即可）。
 */
public final class PgJdbcJsonValue implements JdbcJsonValue {

    private final String type;

    public PgJdbcJsonValue() {
        this("jsonb");
    }

    public PgJdbcJsonValue(String type) {
        this.type = type;
    }

    @Override
    public Object adapt(Object jsonValue) {
        if (jsonValue == null) return null;
        try {
            Class<?> pgClass = Class.forName("org.postgresql.util.PGobject");
            Object pg = pgClass.getDeclaredConstructor().newInstance();
            pgClass.getMethod("setType", String.class).invoke(pg, type);
            pgClass.getMethod("setValue", String.class).invoke(pg, JSON.toJSONString(jsonValue));
            return pg;
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("无法构造 PGobject，请确认 org.postgresql 依赖在类路径中", ex);
        }
    }
}
