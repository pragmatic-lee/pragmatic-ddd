package io.pragmatic.ddd.mybatis.typehandler.json;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.JSONWriter;
import com.alibaba.fastjson2.reader.ObjectReader;
import com.alibaba.fastjson2.reader.ObjectReaderProvider;
import com.alibaba.fastjson2.writer.ObjectWriter;
import com.alibaba.fastjson2.writer.ObjectWriterProvider;
import io.pragmatic.ddd.base.IEnumValue;
import io.pragmatic.ddd.event.spi.IEventSerializer;
import io.pragmatic.ddd.mybatis.spi.JsonSerializer;
import io.pragmatic.ddd.mybatis.typehandler.enums.EnumRule;
import io.pragmatic.ddd.mybatis.typehandler.enums.EnumValueResolver;

import java.lang.reflect.Type;
import java.util.Map;

/**
 * Fastjson2 序列化实现：枚举策略感知、作用域私有，同时实现 {@link IEventSerializer} 服务 Outbox 事件。
 *
 * @author wizard-lee
 */
public final class Fastjson2JsonSerializer implements JsonSerializer, IEventSerializer {

    private final EnumValueResolver resolver;
    private final JSONWriter.Context writeContext;
    private final JSONReader.Context readContext;

    /**
     * @param resolver  共享的枚举解析注册表（运行期反序列化查表用）
     * @param enumRules 枚举 → 持久化策略；序列化维度的唯一来源（不依赖 resolver 是否已预注册）
     */
    public Fastjson2JsonSerializer(EnumValueResolver resolver, Map<Class<?>, EnumRule> enumRules) {
        this.resolver = resolver;
        // 作用域私有：基于各自的 Provider 构造 Context，绝不注册到 fastjson2 全局实例
        ObjectWriterProvider writerProvider = new ObjectWriterProvider();
        ObjectReaderProvider readerProvider = new ObjectReaderProvider();
        for (Map.Entry<Class<?>, EnumRule> e : enumRules.entrySet()) {
            Class<?> t = e.getKey();
            if (!Enum.class.isAssignableFrom(t)) continue;
            @SuppressWarnings("unchecked")
            Class<? extends Enum<?>> et = (Class<? extends Enum<?>>) t;
            registerEnum(writerProvider, readerProvider, et, e.getValue());
        }
        this.writeContext = new JSONWriter.Context(writerProvider, JSONWriter.Feature.FieldBased);
        this.readContext = new JSONReader.Context(readerProvider, JSONReader.Feature.FieldBased);
    }

    @SuppressWarnings("unchecked")
    private void registerEnum(ObjectWriterProvider writerProvider,
                              ObjectReaderProvider readerProvider,
                              Class<? extends Enum<?>> et, EnumRule rule) {
        // 序列化：枚举 → JSON 值（按 rule 取维度）
        ObjectWriter writer = (jw, value, fieldName, fieldType, features) -> {
            Enum<?> en = (Enum<?>) value;
            switch (rule) {
                case NAME    -> jw.writeString(en.name());
                case ORDINAL -> jw.writeInt32(en.ordinal());
                case LABEL   -> jw.writeString(((IEnumValue<?, ?>) en).getName());
                case CODE    -> jw.writeRaw(JSON.toJSONString(((IEnumValue<?, ?>) en).getValue()));
            }
        };
        // 反序列化：JSON 值 → 枚举（复用 resolver 的预建索引，O(1) 查表）
        ObjectReader reader = (jr, type, fieldName, features) -> {
            Object raw = jr.readAny();
            if (raw == null) return null;
            return resolver.resolve((Class) et, raw, rule);
        };
        // 同时注册到 fieldBased 与非 fieldBased 两个缓存（本序列化器用 FieldBased，但兼顾其它使用方式）
        writerProvider.register(et, writer);
        writerProvider.register(et, writer, true);
        readerProvider.register(et, reader);
        readerProvider.register(et, reader, true);
    }

    @Override
    public String serialize(Object obj) {
        if (obj == null) return null;
        return JSON.toJSONString(obj, writeContext);
    }

    @Override
    public Object toJsonValue(Object obj) {
        if (obj == null) return null;
        String text = serialize(obj);
        // 顶层集合 / 数组需按 JSONArray 规整，否则按 JSONObject.class 解析 [..] 会抛 JSONException；
        // 其余按 JSONObject 规整（均应用私有 enum writer 的序列化结果）
        if (obj instanceof java.util.Collection || obj.getClass().isArray()) {
            return JSON.parseArray(text);
        }
        return JSON.parseObject(text, JSONObject.class, readContext);
    }

    @Override
    public <T> T deserialize(String json, Class<T> type) {
        if (json == null || json.isEmpty()) return null;
        return JSON.parseObject(json, type, readContext);
    }

    @Override
    public <T> T fromJsonValue(Object json, Class<T> type) {
        if (json == null) return null;
        String text = toJsonText(json);
        if (text == null || text.isEmpty()) return null;
        return JSON.parseObject(text, type, readContext);
    }

    @Override
    public <T> T deserialize(String json, Type type) {
        if (json == null || json.isEmpty()) return null;
        return JSON.parseObject(json, type, readContext);
    }

    @Override
    public <T> T fromJsonValue(Object json, Type type) {
        if (json == null) return null;
        String text = toJsonText(json);
        if (text == null || text.isEmpty()) return null;
        return JSON.parseObject(text, type, readContext);
    }

    private String toJsonText(Object json) {
        if (json instanceof String s) return s;
        // PostgreSQL 的 PGobject：取其内部 JSON 文本，避免把 {type,value} 当 JSON（无硬依赖，按类名反射）
        if (json.getClass().getName().equals("org.postgresql.util.PGobject")) {
            try {
                return (String) json.getClass().getMethod("getValue").invoke(json);
            } catch (ReflectiveOperationException ex) {
                throw new IllegalStateException("无法读取 PGobject 的 JSON 文本", ex);
            }
        }
        return JSON.toJSONString(json);
    }
}
