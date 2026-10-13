package io.pragmatic.ddd.springboot.bridge;

import io.pragmatic.ddd.config.IConfigurationSource;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 基于 Spring {@link Environment} 的 {@link IConfigurationSource} 实现。
 * 把框架的配置端口直连到 Spring 的外部化配置，使 {@code ConfigurationBinder} 与框架自带的
 * {@code *Properties.bind(source)} 无需再由使用方逐键搬运到内存 Map。
 *
 * @author wizard-lee
 */
public class EnvironmentConfigurationSource implements IConfigurationSource {

    private final Environment environment;

    /**
     * 以 Spring 环境构建配置源。
     *
     * @param environment 承载外部化配置的 Spring 环境
     */
    public EnvironmentConfigurationSource(Environment environment) {
        this.environment = environment;
    }

    @Override
    public String getString(String key, String defaultValue) {
        return this.environment.getProperty(key, defaultValue);
    }

    @Override
    public <T> T get(String key, Class<T> type, T defaultValue) {
        return this.environment.getProperty(key, type, defaultValue);
    }

    @Override
    public boolean contains(String key) {
        return this.environment.containsProperty(key);
    }

    /**
     * 收集当前环境所有可枚举属性源中的键名，供 Binder 按前缀扫描。
     * 非 {@link ConfigurableEnvironment} 或不可枚举的属性源将被跳过。
     *
     * @return 所有可枚举键的集合
     */
    @Override
    public Set<String> keys() {
        if (!(this.environment instanceof ConfigurableEnvironment configurable)) {
            return Set.of();
        }
        Set<String> keys = new LinkedHashSet<>();
        for (PropertySource<?> source : configurable.getPropertySources()) {
            if (source instanceof EnumerablePropertySource<?> enumerable) {
                for (String name : enumerable.getPropertyNames()) {
                    keys.add(name);
                }
            }
        }
        return keys;
    }
}
