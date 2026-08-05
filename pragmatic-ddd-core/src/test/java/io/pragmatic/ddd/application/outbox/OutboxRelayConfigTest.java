package io.pragmatic.ddd.application.outbox;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OutboxRelayConfig 运行配置测试：验证 record 值语义与内置默认值。
 */
class OutboxRelayConfigTest {

    @Test
    void customConfig_holdsAllValues() {
        OutboxRelayConfig config =
                new OutboxRelayConfig(Duration.ofSeconds(10), Duration.ofSeconds(5), 50, 3);

        assertThat(config.pollInterval()).isEqualTo(Duration.ofSeconds(10));
        assertThat(config.grace()).isEqualTo(Duration.ofSeconds(5));
        assertThat(config.batchSize()).isEqualTo(50);
        assertThat(config.maxAttempts()).isEqualTo(3);
    }

    @Test
    void defaultConfig_matchesDocumentedDefaults() {
        OutboxRelayConfig config = OutboxRelayConfig.defaultConfig();

        assertThat(config.pollInterval()).isEqualTo(Duration.ofMinutes(5));
        assertThat(config.grace()).isEqualTo(Duration.ofSeconds(30));
        assertThat(config.batchSize()).isEqualTo(200);
        assertThat(config.maxAttempts()).isEqualTo(10);
    }
}
