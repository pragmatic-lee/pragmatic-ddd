package io.pragmatic.ddd.config;

import io.pragmatic.ddd.config.context.IConfigurationContext;
import io.pragmatic.ddd.config.feature.FeatureContext;
import io.pragmatic.ddd.config.feature.IFeatureToggle;

import java.util.Optional;

/**
 * 统一门面式配置抽象基类。
 * <p>
 * 持有 L1 配置源与 L3 特性开关，子类基于聚合维度编写语义方法，
 * 方法内部通过受保护 helper 读取配置，调用方无需感知裸 key。
 *
 * @author wizard-lee
 */
public abstract class AbstractConfiguration {

    private final IConfigurationContext context;

    /**
     * 基于配置上下文构建抽象配置。
     *
     * @param context 配置上下文
     */
    protected AbstractConfiguration(IConfigurationContext context) {
        this.context = context;
    }

    /**
     * 读取指定 key 的原始字符串值（缺失返回兜底值）。
     *
     * @param key 配置键
     * @param def 兜底值
     * @return 配置值或兜底值
     */
    protected String value(String key, String def) {
        return context.source().getString(key, def);
    }

    /**
     * 读取指定 key 的类型化值（缺失返回兜底值）。
     *
     * @param key  配置键
     * @param type 目标类型
     * @param def  兜底值
     * @param <T>  目标类型
     * @return 配置值或兜底值
     */
    protected <T> T value(String key, Class<T> type, T def) {
        return context.source().get(key, type, def);
    }

    /**
     * 判断指定特性开关是否开启（无灰度上下文）。
     *
     * @param key 特性键
     * @return 是否开启
     */
    protected boolean feature(String key) {
        return featureToggle().isEnabled(key);
    }

    /**
     * 判断指定特性开关在给定灰度上下文中是否放量。
     *
     * @param key 特性键
     * @param ctx 灰度上下文
     * @return 是否放量
     */
    protected boolean feature(String key, FeatureContext ctx) {
        return featureToggle().isEnabled(key, ctx);
    }

    /**
     * 将指定前缀下的配置绑定为类型化对象（宽松模式，缺失字段保留默认值）。
     *
     * @param prefix 键值前缀
     * @param type   目标类型
     * @param <T>    目标类型
     * @return 绑定后的实例
     */
    protected <T> T bind(String prefix, Class<T> type) {
        return ConfigurationBinder.bind(context.source(), prefix, type);
    }

    /**
     * 返回底层配置源（高级用法）。
     *
     * @return 配置源
     */
    protected IConfigurationSource source() {
        return context.source();
    }

    /**
     * 返回底层特性开关（高级用法）。
     *
     * @return 特性开关
     */
    protected IFeatureToggle featureToggle() {
        return context.featureToggle();
    }

    /**
     * 返回底层配置上下文（高级用法）。
     *
     * @return 配置上下文
     */
    protected IConfigurationContext context() {
        return context;
    }
}
