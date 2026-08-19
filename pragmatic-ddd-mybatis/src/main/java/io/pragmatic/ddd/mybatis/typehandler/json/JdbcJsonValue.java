package io.pragmatic.ddd.mybatis.typehandler.json;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

/**
 * 把结构化 JSON 值适配为当前 JDBC 驱动可 {@code setObject} 的形态，避免 handler 耦合具体驱动。
 * 默认 {@link #DEFAULT} 透传，MySQL 用 {@link #MYSQL}，PostgreSQL 用 {@link PgJdbcJsonValue}。
 *
 * @author wizard-lee
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
