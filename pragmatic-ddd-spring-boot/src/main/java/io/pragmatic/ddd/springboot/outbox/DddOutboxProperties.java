package io.pragmatic.ddd.springboot.outbox;

import io.pragmatic.ddd.application.outbox.OutboxRelayConfig;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Outbox 链路的可配置项，前缀 {@code pragmatic-ddd.outbox}。
 * 把示例中散落的硬编码参数（线程池大小、轮询间隔等）收敛为外部化配置。
 *
 * @author wizard-lee
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "pragmatic-ddd.outbox")
public class DddOutboxProperties {

    /** 是否启用 outbox 链路自动装配，默认开启。 */
    private boolean enabled = true;

    /** 提交后主动推送器的线程池大小。 */
    private int publisherPoolSize = 4;

    /** 兜底轮询间隔。 */
    private Duration pollInterval = Duration.ofMinutes(5);

    /** 认领宽限期：仅轮询创建时间早于 now-grace 的 PENDING 记录，避让 eager 路径。 */
    private Duration grace = Duration.ofSeconds(30);

    /** 单轮认领的最大记录数。 */
    private int batchSize = 200;

    /** 单条记录的最大重试次数，超过转死信。 */
    private int maxAttempts = 10;

    /**
     * 依据当前配置构造框架的 {@link OutboxRelayConfig}。
     *
     * @return 兜底轮询运行配置
     */
    public OutboxRelayConfig toRelayConfig() {
        return new OutboxRelayConfig(this.pollInterval, this.grace, this.batchSize, this.maxAttempts);
    }
}
