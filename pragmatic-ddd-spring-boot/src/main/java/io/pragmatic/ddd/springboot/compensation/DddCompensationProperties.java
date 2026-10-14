package io.pragmatic.ddd.springboot.compensation;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 补偿链路的可配置项，前缀 {@code pragmatic-ddd.compensation}。
 * 把示例中散落的模式开关与轮询参数收敛为外部化配置。
 *
 * <p>块级开关 {@code enabled} 不在此声明，仍由自动配置类上的条件注解独占，避免同一语义出现两个来源。</p>
 *
 * @author wizard-lee
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "pragmatic-ddd.compensation")
public class DddCompensationProperties {

    /** 补偿模式：in-memory（不落 WAL，DB 零依赖）| durable（WAL + 中继兜底）。 */
    private String mode = "in-memory";

    /** durable 模式下补偿中继的轮询间隔。 */
    private Duration relayPollInterval = Duration.ofSeconds(10);
}
