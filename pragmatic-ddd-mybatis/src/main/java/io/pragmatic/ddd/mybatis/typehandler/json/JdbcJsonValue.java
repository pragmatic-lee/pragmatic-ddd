package io.pragmatic.ddd.mybatis.typehandler.json;

/**
 * 把结构化 JSON 值适配为当前 JDBC 驱动可 {@code setObject} 的 JSON 形态；零驱动强依赖（按需 SPI/构造注入）。
 *
 * <p>不同 JDBC 驱动对"原生 JSON 列"接受的入参形态不一：
 * <ul>
 *   <li>MySQL Connector/J 8：JSON 列可直接 {@code setObject(i, jsonString)} 或 JSONObject，驱动按列类型转 JSON；</li>
 *   <li>PostgreSQL：必须传驱动专属的 {@code org.postgresql.util.PGobject}（设 type 为 jsonb/json），
 *       裸 JSONObject 会被当作 unknown 类型报错。</li>
 * </ul>
 * 为避免 handler 耦合具体驱动，引入此轻量适配层；默认 {@link #DEFAULT} 直接透传（兼容 MySQL 等）。
 */
public interface JdbcJsonValue {

    /** 由 {@code serializer.toJsonValue} 产出的 JSONObject 转成驱动可接受的入参。 */
    Object adapt(Object jsonValue);

    /** 默认实现：返回原始 JSONObject（MySQL 等直接接受 String/JSONObject 的驱动）。 */
    JdbcJsonValue DEFAULT = json -> json;
}
