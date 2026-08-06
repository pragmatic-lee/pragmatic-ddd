package io.pragmatic.ddd.config.feature;

import io.pragmatic.ddd.config.IConfigurationSource;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * 基于白名单的灰度策略（内置实现）。
 * 维度名完全由配置决定，框架不预设任何灰度维度（如 userId/accountId 等）。
 * 白名单按维度分组配置：{@code {featureKey}.allow.{dimension}=值1,值2,...}；
 * 当灰度上下文中任意维度的取值命中其对应维度的白名单时即视为放量。
 *
 * @author wizard-lee
 */
public final class WhitelistGrayStrategy implements IGrayStrategy {

    private static final String ALLOW_PREFIX = ".allow.";

    private final IConfigurationSource source;

    /** 构造策略。 */
    public WhitelistGrayStrategy(IConfigurationSource source) {
        this.source = source;
    }

    @Override
    public boolean matches(String featureKey, FeatureContext context) {
        String allowRoot = featureKey + ALLOW_PREFIX;
        return context.dimensions()
                .anyMatch(dimension -> hits(dimension, allowRoot, context.getDimension(dimension).orElse("")));
    }

    private boolean hits(String dimension, String allowRoot, String value) {
        if (value.isBlank()) {
            return false;
        }
        String allow = source.getString(allowRoot + dimension, "");
        if (allow == null || allow.isBlank()) {
            return false;
        }
        List<String> whitelist = Arrays.stream(allow.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        return whitelist.contains(value);
    }
}
