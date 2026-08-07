package io.pragmatic.ddd.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 ConfigurationBinder 将配置源绑定到 record 与 POJO 的类型转换。
 *
 * @author wizard-lee
 */
class ConfigurationBinderTest {

    @Test
    void shouldBindRecordWithVariousTypes() {
        MapConfigurationSource source = new MapConfigurationSource();
        source.put("outbox.poll-interval", "PT5M");
        source.put("outbox.batch-size", "200");
        source.put("outbox.enabled", "true");
        source.put("outbox.mode", "LAZY");

        OutboxConfig config = ConfigurationBinder.bind(source, "outbox", OutboxConfig.class);

        assertThat(config.pollInterval()).isEqualTo(Duration.ofMinutes(5));
        assertThat(config.batchSize()).isEqualTo(200);
        assertThat(config.enabled()).isTrue();
        assertThat(config.mode()).isEqualTo(Mode.LAZY);
    }

    @Test
    void shouldBindPojoWithSetters() {
        MapConfigurationSource source = new MapConfigurationSource();
        source.put("mq.name", "order-topic");
        source.put("mq.threads", "8");

        MqConfig config = ConfigurationBinder.bind(source, "mq", MqConfig.class);

        assertThat(config.getName()).isEqualTo("order-topic");
        assertThat(config.getThreads()).isEqualTo(8);
    }

    @Test
    void shouldThrowWhenRequiredKeyMissing() {
        MapConfigurationSource source = new MapConfigurationSource();
        source.put("outbox.batch-size", "200");

        assertThatThrownBy(() -> ConfigurationBinder.bind(source, "outbox", OutboxConfig.class))
                .isInstanceOf(ConfigurationBindingException.class)
                .hasMessageContaining("outbox.poll-interval");
    }

    @Test
    void sourceShouldConvertScalarTypes() {
        MapConfigurationSource source = new MapConfigurationSource();
        source.put("i", "10");
        source.put("l", "100");
        source.put("d", "1.5");
        source.put("b", "true");

        assertThat(source.get("i", Integer.class, 0)).isEqualTo(10);
        assertThat(source.get("l", Long.class, 0L)).isEqualTo(100L);
        assertThat(source.get("d", Double.class, 0.0)).isEqualTo(1.5);
        assertThat(source.get("b", Boolean.class, false)).isTrue();
        assertThat(source.get("missing", String.class, "def")).isEqualTo("def");
    }

    enum Mode {
        EAGER, LAZY
    }

    record OutboxConfig(Duration pollInterval, int batchSize, boolean enabled, Mode mode) {
    }

    public static class MqConfig {
        private String name;
        private int threads;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getThreads() {
            return threads;
        }

        public void setThreads(int threads) {
            this.threads = threads;
        }
    }
}
