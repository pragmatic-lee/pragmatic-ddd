package io.pragmatic.ddd.config.feature;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 特性开关的灰度上下文，承载用于灰度判定的维度键值对。
 * 维度名由业务自定义，框架不预设任何灰度维度（如 userId/accountId 等）。
 *
 * @author wizard-lee
 */
public final class FeatureContext {

    private final Map<String, String> dimensions;

    /** 构造空上下文。 */
    public FeatureContext() {
        this.dimensions = new HashMap<>();
    }

    private FeatureContext(Map<String, String> dimensions) {
        this.dimensions = new HashMap<>(dimensions);
    }

    /**
     * 写入一个维度。
     *
     * @param name  维度名（由业务自定义，如 "userId"、"shopId"）
     * @param value 维度值
     * @return 当前实例，便于链式装配
     */
    public FeatureContext with(String name, String value) {
        dimensions.put(name, value);
        return this;
    }

    /**
     * 读取维度值。
     *
     * @param name 维度名
     * @return 可能为空的维度值
     */
    public Optional<String> getDimension(String name) {
        return Optional.ofNullable(dimensions.get(name));
    }

    /**
     * 返回上下文中所有维度名（用于灰度策略遍历判定）。
     *
     * @return 维度名流
     */
    public java.util.stream.Stream<String> dimensions() {
        return dimensions.keySet().stream();
    }

    /** 当前上下文中是否存在任意维度。 */
    public boolean hasDimensions() {
        return !dimensions.isEmpty();
    }
}
