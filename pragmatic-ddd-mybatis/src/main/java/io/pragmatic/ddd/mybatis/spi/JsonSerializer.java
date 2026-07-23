package io.pragmatic.ddd.mybatis.spi;

/**
 * JSON 序列化插口；默认实现亦实现 core 的 {@code IEventSerializer}，确保与事件/Outbox 同栈。
 *
 * <p>与 core 的 {@link io.pragmatic.ddd.event.spi.IEventSerializer} 同形（方法签名兼容），
 * 便于默认实现同时服务 Outbox 事件，保证全框架 JSON 行为一致。
 */
public interface JsonSerializer {

    /** 序列化为 JSON 文本，落库字符串/日志/Outbox 事件用。 */
    String serialize(Object obj);

    /** 序列化为结构化 JSON 值（JSONObject/JSONArray），供 {@code PreparedStatement.setObject} 写入原生 JSON 列。 */
    Object toJsonValue(Object obj);

    /** 从 JSON 文本还原对象（VARCHAR 列或 Outbox 场景）。 */
    <T> T deserialize(String json, Class<T> type);

    /** 从 setObject/getObject 读回的任意 JSON 形态（String / JSONObject / Map / PGobject）还原对象。 */
    <T> T fromJsonValue(Object json, Class<T> type);

    /** 按参数化类型还原（如 List<Address>），供 ListTypeHandler 精确反序列化。 */
    <T> T deserialize(String json, java.lang.reflect.Type type);

    /** 从任意 JSON 形态按参数化类型还原，供 ListTypeHandler 精确反序列化。 */
    <T> T fromJsonValue(Object json, java.lang.reflect.Type type);
}
