package io.pragmatic.ddd.application.outbox;

import io.pragmatic.ddd.config.ConfigurationBinder;
import io.pragmatic.ddd.config.IConfigurationSource;
import java.time.Duration;

/**
 * OutboxRelay 的运行配置（不可变）。
 *
 * @author wizard-lee
 */
public record OutboxRelayConfig(Duration pollInterval, Duration grace, int batchSize, int maxAttempts) {

    /** 内置默认配置（pollInterval=5min、grace=30s、batchSize=200、maxAttempts=10）。 */
    public static OutboxRelayConfig defaultConfig() {
        return new OutboxRelayConfig(
                Duration.ofMinutes(5),
                Duration.ofSeconds(30),
                200,
                10);
    }

    /**
     * 从配置源按 {@code outbox} 前缀绑定运行配置（兼容并收敛既有配置）。
     * 键约定：outbox.poll-interval / outbox.grace / outbox.batch-size / outbox.max-attempts。
     *
     * @param source 配置源
     * @return 绑定后的配置
     */
    public static OutboxRelayConfig bind(IConfigurationSource source) {
        return ConfigurationBinder.bind(source, "outbox", OutboxRelayConfig.class);
    }
}
