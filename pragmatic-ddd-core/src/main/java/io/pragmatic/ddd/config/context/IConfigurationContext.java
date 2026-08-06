package io.pragmatic.ddd.config.context;

import io.pragmatic.ddd.config.IConfigurationSource;
import io.pragmatic.ddd.config.feature.IFeatureToggle;

/**
 * 配置上下文门面接口，聚合 L1 配置源与 L3 特性开关。
 * 使用方基于聚合写语义配置时，仅需注入此上下文即可获取全部配置能力。
 *
 * @author wizard-lee
 */
public interface IConfigurationContext {

    /**
     * 返回底层 L1 配置源。
     *
     * @return 配置源
     */
    IConfigurationSource source();

    /**
     * 返回底层 L3 特性开关。
     *
     * @return 特性开关
     */
    IFeatureToggle featureToggle();
}
