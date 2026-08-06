package io.pragmatic.ddd.config.context;

import io.pragmatic.ddd.config.IConfigurationSource;
import io.pragmatic.ddd.config.feature.IFeatureToggle;
import io.pragmatic.ddd.config.feature.MapFeatureToggle;

/**
 * 默认配置上下文实现，聚合内存态的配置源与特性开关。
 *
 * @author wizard-lee
 */
public final class DefaultConfigurationContext implements IConfigurationContext {

    private final IConfigurationSource source;
    private final IFeatureToggle featureToggle;

    /**
     * 基于给定配置源构建上下文，特性开关由配置源自动派生。
     *
     * @param source 配置源
     */
    public DefaultConfigurationContext(IConfigurationSource source) {
        this(source, MapFeatureToggle.from(source));
    }

    /**
     * 基于给定配置源与特性开关构建上下文。
     *
     * @param source        配置源
     * @param featureToggle 特性开关
     */
    public DefaultConfigurationContext(IConfigurationSource source, IFeatureToggle featureToggle) {
        this.source = source;
        this.featureToggle = featureToggle;
    }

    @Override
    public IConfigurationSource source() {
        return source;
    }

    @Override
    public IFeatureToggle featureToggle() {
        return featureToggle;
    }
}
