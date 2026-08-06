package io.pragmatic.ddd.config.feature;

/**
 * 特性开关抽象（L3 语义层）。
 * 在原始键值之上提供具备业务语义的开关判定，并支持灰度过半（指定人/账号/条件放量）。
 *
 * @author wizard-lee
 */
public interface IFeatureToggle {

    /**
     * 判断特性是否开启，命中灰度策略时亦视为开启。
     *
     * @param featureKey 特性键
     * @return 是否开启
     */
    boolean isEnabled(String featureKey);

    /**
     * 判断特性是否开启，缺失或未配置时返回默认值。
     *
     * @param featureKey    特性键
     * @param defaultValue 默认值
     * @return 是否开启
     */
    boolean isEnabled(String featureKey, boolean defaultValue);

    /**
     * 携带灰度上下文判断特性是否开启（用于按人/账号/条件放量）。
     *
     * @param featureKey 特性键
     * @param context    灰度上下文
     * @return 是否开启
     */
    boolean isEnabled(String featureKey, FeatureContext context);

    /**
     * 返回特性的当前生效状态。
     *
     * @param featureKey 特性键
     * @return 生效状态
     */
    ToggleState stateOf(String featureKey);
}
