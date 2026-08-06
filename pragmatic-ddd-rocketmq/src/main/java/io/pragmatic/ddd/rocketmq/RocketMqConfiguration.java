package io.pragmatic.ddd.rocketmq;

import io.pragmatic.ddd.config.AbstractConfiguration;
import io.pragmatic.ddd.config.context.IConfigurationContext;

/**
 * RocketMQ 聚合配置门面。
 * 基于统一配置上下文，按聚合维度暴露 RocketMQ 语义配置，调用方无需感知裸 key。
 *
 * @author wizard-lee
 */
public final class RocketMqConfiguration extends AbstractConfiguration {

    /**
     * 基于配置上下文构建 RocketMQ 聚合配置。
     *
     * @param context 配置上下文
     */
    public RocketMqConfiguration(IConfigurationContext context) {
        super(context);
    }

    /**
     * 返回 RocketMQ 统一配置（前缀 {@code rocketmq}）。
     *
     * @return 绑定后的 RocketMqConfig
     */
    public RocketMqConfig config() {
        return bind("rocketmq", RocketMqConfig.class);
    }

    /**
     * 返回 NameServer 地址（Remoting 协议，框架自建 Producer/Consumer 时需要）。
     *
     * @return NameServer 地址
     */
    public String nameServer() {
        return value("rocketmq.name-server", (String) null);
    }

    /**
     * 返回 gRPC Proxy 地址（5.x 协议，可选）。
     *
     * @return Proxy 地址
     */
    public String proxyAddr() {
        return value("rocketmq.proxy-addr", (String) null);
    }
}
