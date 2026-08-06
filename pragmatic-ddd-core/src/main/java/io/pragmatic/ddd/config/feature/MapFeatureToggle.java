package io.pragmatic.ddd.config.feature;

import io.pragmatic.ddd.config.IConfigurationSource;

/**
 * 基于内存配置源的特性开关实现。
 * 约定配置项 {@code {featureKey}} 取值为 OFF/ROLLOUT/ON（缺省按默认值或 OFF 处理）；
 * ROLLOUT 状态下通过灰度策略（默认白名单）判定是否对当前上下文放量。
 *
 * @author wizard-lee
 */
public final class MapFeatureToggle implements IFeatureToggle {

    private static final String STATE_OFF = "OFF";
    private static final String STATE_ROLLOUT = "ROLLOUT";
    private static final String STATE_ON = "ON";

    private final IConfigurationSource source;
    private final IGrayStrategy grayStrategy;

    /** 构造。 */
    public MapFeatureToggle(IConfigurationSource source) {
        this(source, new WhitelistGrayStrategy(source));
    }

    /** 构造并自定义灰度策略。 */
    public MapFeatureToggle(IConfigurationSource source, IGrayStrategy grayStrategy) {
        this.source = source;
        this.grayStrategy = grayStrategy;
    }

    /** 从配置源构造。 */
    public static MapFeatureToggle from(IConfigurationSource source) {
        return new MapFeatureToggle(source);
    }

    @Override
    public boolean isEnabled(String featureKey) {
        return isEnabled(featureKey, false);
    }

    @Override
    public boolean isEnabled(String featureKey, boolean defaultValue) {
        ToggleState state = stateOf(featureKey);
        if (state == ToggleState.ON) {
            return true;
        }
        return defaultValue;
    }

    @Override
    public boolean isEnabled(String featureKey, FeatureContext context) {
        ToggleState state = stateOf(featureKey);
        if (state == ToggleState.ON) {
            return true;
        }
        if (state == ToggleState.OFF) {
            return false;
        }
        return grayStrategy.matches(featureKey, context);
    }

    @Override
    public ToggleState stateOf(String featureKey) {
        String raw = source.getString(featureKey, STATE_OFF);
        if (raw == null) {
            return ToggleState.OFF;
        }
        return switch (raw.trim().toUpperCase()) {
            case STATE_ON -> ToggleState.ON;
            case STATE_ROLLOUT -> ToggleState.ROLLOUT;
            default -> ToggleState.OFF;
        };
    }
}
