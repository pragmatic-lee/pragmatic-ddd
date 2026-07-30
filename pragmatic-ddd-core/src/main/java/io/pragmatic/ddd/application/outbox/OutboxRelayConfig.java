package io.pragmatic.ddd.application.outbox;

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
}
