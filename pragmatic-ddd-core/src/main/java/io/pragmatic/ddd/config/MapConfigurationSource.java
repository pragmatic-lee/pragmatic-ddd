package io.pragmatic.ddd.config;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于内存 Map 的配置源实现，可作为测试替身或轻量运行态配置。
 * 存储以字符串为基准，读取时按目标类型做转换；未知类型或非法的转换返回默认值。
 *
 * @author wizard-lee
 */
public final class MapConfigurationSource implements IConfigurationSource {

    private final Map<String, String> store;

    /** 构造空配置源。 */
    public MapConfigurationSource() {
        this.store = new ConcurrentHashMap<>();
    }

    /**
     * 构造并批量载入初始条目。
     *
     * @param initial 初始键值对
     */
    public MapConfigurationSource(Map<String, String> initial) {
        this.store = new ConcurrentHashMap<>(initial);
    }

    /**
     * 写入一个条目（供测试或运行态动态装配）。
     *
     * @param key   配置键
     * @param value 配置值
     * @return 当前实例，便于链式装配
     */
    public MapConfigurationSource put(String key, String value) {
        store.put(key, value);
        return this;
    }

    @Override
    public String getString(String key, String defaultValue) {
        return store.getOrDefault(key, defaultValue);
    }

    @Override
    public <T> T get(String key, Class<T> type, T defaultValue) {
        String raw = store.get(key);
        if (raw == null) {
            return defaultValue;
        }
        return convert(raw, type, defaultValue);
    }

    @Override
    public boolean contains(String key) {
        return store.containsKey(key);
    }

    @Override
    public Set<String> keys() {
        return Set.copyOf(store.keySet());
    }

    private <T> T convert(String raw, Class<T> type, T defaultValue) {
        if (type == String.class) {
            return type.cast(raw);
        }
        if (type == Integer.class || type == int.class) {
            return type.cast(Integer.parseInt(raw.trim()));
        }
        if (type == Long.class || type == long.class) {
            return type.cast(Long.parseLong(raw.trim()));
        }
        if (type == Boolean.class || type == boolean.class) {
            return type.cast(Boolean.parseBoolean(raw.trim()));
        }
        if (type == Double.class || type == double.class) {
            return type.cast(Double.parseDouble(raw.trim()));
        }
        if (type == Float.class || type == float.class) {
            return type.cast(Float.parseFloat(raw.trim()));
        }
        if (type.isEnum()) {
            return type.cast(Enum.valueOf(type.asSubclass(Enum.class), raw.trim()));
        }
        if (type == java.time.Duration.class) {
            return type.cast(java.time.Duration.parse(raw.trim()));
        }
        return defaultValue;
    }

    /** 供 Binder 内部按前缀读取原始条目。 */
    Map<String, String> rawStore() {
        return new HashMap<>(store);
    }
}
