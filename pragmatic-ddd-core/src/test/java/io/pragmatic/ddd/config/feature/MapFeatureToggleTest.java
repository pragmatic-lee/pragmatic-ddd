package io.pragmatic.ddd.config.feature;

import io.pragmatic.ddd.config.IConfigurationSource;
import io.pragmatic.ddd.config.MapConfigurationSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MapFeatureToggleTest {

    @Test
    void offStateReturnsFalse() {
        IConfigurationSource source = new MapConfigurationSource().put("order.discount.v2", "OFF");
        IFeatureToggle toggle = new MapFeatureToggle(source);

        assertThat(toggle.isEnabled("order.discount.v2")).isFalse();
        assertThat(toggle.stateOf("order.discount.v2")).isEqualTo(ToggleState.OFF);
    }

    @Test
    void onStateReturnsTrue() {
        IConfigurationSource source = new MapConfigurationSource().put("order.discount.v2", "ON");
        IFeatureToggle toggle = new MapFeatureToggle(source);

        assertThat(toggle.isEnabled("order.discount.v2")).isTrue();
        assertThat(toggle.isEnabled("order.discount.v2", new FeatureContext().with("userId", "u1"))).isTrue();
        assertThat(toggle.stateOf("order.discount.v2")).isEqualTo(ToggleState.ON);
    }

    @Test
    void rolloutStateOnlyTrueWhenWhitelistHit() {
        // 维度名完全由配置决定：按 {featureKey}.allow.{dimension} 分组，框架不预设维度
        IConfigurationSource source = new MapConfigurationSource()
                .put("order.discount.v2", "ROLLOUT")
                .put("order.discount.v2.allow.userId", "u1,u2")
                .put("order.discount.v2.allow.shopId", "s9");
        IFeatureToggle toggle = new MapFeatureToggle(source);

        assertThat(toggle.stateOf("order.discount.v2")).isEqualTo(ToggleState.ROLLOUT);
        // 无上下文：保守返回 false
        assertThat(toggle.isEnabled("order.discount.v2")).isFalse();
        // userId 白名单命中：放量
        assertThat(toggle.isEnabled("order.discount.v2", new FeatureContext().with("userId", "u1"))).isTrue();
        // userId 白名单外：不放量
        assertThat(toggle.isEnabled("order.discount.v2", new FeatureContext().with("userId", "u9"))).isFalse();
        // shopId 白名单命中（验证框架不写死维度名）：放量
        assertThat(toggle.isEnabled("order.discount.v2", new FeatureContext().with("shopId", "s9"))).isTrue();
    }

    @Test
    void missingKeyReturnsDefault() {
        IConfigurationSource source = new MapConfigurationSource();
        IFeatureToggle toggle = new MapFeatureToggle(source);

        assertThat(toggle.isEnabled("unknown.feature", true)).isTrue();
        assertThat(toggle.isEnabled("unknown.feature", false)).isFalse();
        assertThat(toggle.stateOf("unknown.feature")).isEqualTo(ToggleState.OFF);
    }
}
