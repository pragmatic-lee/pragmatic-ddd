package io.pragmatic.ddd.config.feature;

/**
 * 灰度策略接口（SPI）。
 * 在特性处于 ROLLOUT 状态时，由具体策略决定是否对当前上下文放量。
 *
 * @author wizard-lee
 */
public interface IGrayStrategy {

    /**
     * 判断给定上下文是否命中灰度放量。
     *
     * @param featureKey 特性键
     * @param context    灰度上下文
     * @return 是否命中
     */
    boolean matches(String featureKey, FeatureContext context);
}
