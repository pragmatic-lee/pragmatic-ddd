package io.pragmatic.ddd.mybatis.typehandler.json;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

/**
 * 把结构化 JSON 值适配为当前 JDBC 驱动可 {@code setObject} 的 JSON 形态；零驱动强依赖（按需 SPI/构造注入）。
 *
 * <p>不同 JDBC 驱动对"原生 JSON 列"接受的入参形态不一：
 * <ul>
 *   <li>MySQL Connector/J 8：JSON 列应按列类型接收 JSON 文本字符串；直接 {@code setObject(i, JSONObject)}
 *       会被驱动当作 binary 字符串而报 {@code CHARACTER SET 'binary'} 错，故需序列化为文本。</li>
 *   <li>PostgreSQL：必须传驱动专属的 {@code org.postgresql.util.PGobject}（设 type 为 jsonb/json），
 *       裸 JSONObject 会被当作 unknown 类型报错。</li>
 * </ul>
 * 为避免 handler 耦合具体驱动，引入此轻量适配层；默认 {@link #DEFAULT} 直接透传，MySQL 场景用 {@link #MYSQL}。
 */
public interface JdbcJsonValue {

    /** 由 {@code serializer.toJsonValue} 产出的 JSONObject 转成驱动可接受的入参。 */
    Object adapt(Object jsonValue);

    /** 默认实现：返回原始 JSONObject（用于驱动可直接接受 JSONObject 的场景）。 */
    JdbcJsonValue DEFAULT = json -> json;

    /** MySQL 适配：把 JSONObject/JSONArray 序列化为 JSON 文本，兼容驱动对 JSON 列的写入要求。 */
    JdbcJsonValue MYSQL = json -> {
        if (json == null) {
            return null;
        }
        if (json instanceof JSONObject || json instanceof JSONArray) {
            return JSON.toJSONString(json);
        }
        return json;
    };
}
