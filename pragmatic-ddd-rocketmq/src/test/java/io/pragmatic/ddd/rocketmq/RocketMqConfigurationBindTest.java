package io.pragmatic.ddd.rocketmq;

import io.pragmatic.ddd.config.MapConfigurationSource;
import io.pragmatic.ddd.config.context.DefaultConfigurationContext;
import io.pragmatic.ddd.config.context.IConfigurationContext;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RocketMqConfigurationBindTest {

    @Test
    void shouldBindRocketMqConfigFromSource() {
        MapConfigurationSource source = new MapConfigurationSource(Map.of(
                "pragmatic-ddd.event.rocketmq.name-server", "127.0.0.1:9876",
                "pragmatic-ddd.event.rocketmq.proxy-addr", "127.0.0.1:8081",
                "pragmatic-ddd.event.rocketmq.retry-times-when-send-failed", "5",
                "pragmatic-ddd.event.rocketmq.send-msg-timeout", "5000",
                "pragmatic-ddd.event.rocketmq.max-reconsume-times", "20"));
        IConfigurationContext context = new DefaultConfigurationContext(source);
        RocketMqConfiguration configuration = new RocketMqConfiguration(context);

        RocketMqProperties config = configuration.config();
        assertThat(config.getNameServer()).isEqualTo("127.0.0.1:9876");
        assertThat(config.getProxyAddr()).isEqualTo("127.0.0.1:8081");
        assertThat(config.getRetryTimesWhenSendFailed()).isEqualTo(5);
        assertThat(config.getSendMsgTimeout()).isEqualTo(5000);
        assertThat(config.getMaxReconsumeTimes()).isEqualTo(20);
        assertThat(config.getCompressMsgBodyOverHowmuch()).isEqualTo(4096);
        assertThat(config.getConsumerGroup()).isEqualTo("PRAGMATIC_DDD_RMQ_CONSUMER");
    }

    @Test
    void shouldExposeAggregatedAccessors() {
        MapConfigurationSource source = new MapConfigurationSource(Map.of(
                "pragmatic-ddd.event.rocketmq.name-server", "localhost:9876"));
        RocketMqConfiguration configuration = new RocketMqConfiguration(new DefaultConfigurationContext(source));

        assertThat(configuration.nameServer()).isEqualTo("localhost:9876");
        assertThat(configuration.proxyAddr()).isNull();
    }
}
