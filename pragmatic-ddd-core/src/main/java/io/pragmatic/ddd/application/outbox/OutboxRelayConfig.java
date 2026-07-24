package io.pragmatic.ddd.application.outbox;

import java.time.Duration;

/**
 * OutboxRelay 的运行配置（不可变）。
 *
 * <p>由基础设施（Spring Boot 自动配置）从 {@code pragmatic-ddd.outbox.relay.*} 绑定后注入；
 * 也可用 {@link #defaultConfig()} 获取内置默认值（与提案第五章 YAML 默认值一致）。</p>
 *
 * @param pollInterval 兜底轮询周期，默认 5 分钟
 * @param grace        仅认领 age>grace 的 PENDING，默认 30s
 * @param batchSize    单次认领批大小，默认 200
 * @param maxAttempts  重试上限，超过转 FAILED，默认 10
 * @author Li XiaoJing
 * @since 2.2.0
 */
public record OutboxRelayConfig(Duration pollInterval, Duration grace, int batchSize, int maxAttempts) {

    /**
     * 内置默认配置（与提案 YAML 默认值一致）。
     */
    public static OutboxRelayConfig defaultConfig() {
        return new OutboxRelayConfig(
                Duration.ofMinutes(5),
                Duration.ofSeconds(30),
                200,
                10);
    }
}
