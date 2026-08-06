package io.pragmatic.ddd.config;

import java.util.Optional;
import java.util.Set;

/**
 * 配置原始键值源（L1 抽象）。
 * 屏蔽底层配置后端（内存 Map、Spring Environment、Nacos、Apollo 等）的差异，
 * 仅暴露原始字符串键值的读取能力；类型化与语义化由其上的 Binder 与 FeatureToggle 完成。
 *
 * @author wizard-lee
 */
public interface IConfigurationSource {

    /**
     * 读取字符串值，缺失时返回默认值。
     *
     * @param key          配置键
     * @param defaultValue 缺失时的默认值
     * @return 配置值或默认值
     */
    String getString(String key, String defaultValue);

    /**
     * 读取并转换指定类型的值，缺失或转换失败时返回默认值。
     *
     * @param key          配置键
     * @param type         目标类型
     * @param defaultValue 缺失或转换失败时的默认值
     * @param <T>          值类型
     * @return 转换后的值或默认值
     */
    <T> T get(String key, Class<T> type, T defaultValue);

    /**
     * 判断指定键是否存在。
     *
     * @param key 配置键
     * @return 是否存在
     */
    boolean contains(String key);

    /**
     * 返回当前源中所有键的集合（用于 Binder 按前缀扫描）。
     *
     * @return 所有键
     */
    Set<String> keys();

    /**
     * 以 Optional 形式读取字符串值，缺失时为空。
     *
     * @param key 配置键
     * @return 可能为空的配置值
     */
    default Optional<String> find(String key) {
        if (contains(key)) {
            return Optional.ofNullable(getString(key, null));
        }
        return Optional.empty();
    }
}
