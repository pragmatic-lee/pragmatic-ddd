package io.pragmatic.ddd.rocketmq;

import io.pragmatic.ddd.config.ConfigurationBinder;
import io.pragmatic.ddd.config.IConfigurationSource;
import lombok.Getter;

/**
 * RocketMQ 统一配置类。
 * <p>
 * nameServer 仅在框架自建 Producer/Consumer 时需要；
 * 如果外部注入 Producer，则 nameServer 可不配。
 * proxyAddr 仅在 gRPC 实现时需要（rocketmq-client-java 可选依赖）。
 * <p>
 * getter 由 Lombok 自动生成；setter 因需保持链式调用（返回 this）保留手写。
 *
 * @author wizard-lee
 */
@Getter
public class RocketMqConfig {

    /** NameServer 地址（Remoting 协议，框架自建 Producer/Consumer 时需要） */
    private String nameServer;
    /** gRPC Proxy 地址（5.x 协议，可选） */
    private String proxyAddr;

    // ── Producer 配置（仅框架自建时生效）──
    private int retryTimesWhenSendFailed = 3;
    private int sendMsgTimeout = 3000;
    private int compressMsgBodyOverHowmuch = 4096;
    private String producerGroup = "DEFAULT_PRODUCER_GROUP";
    private int defaultDelayLevel = 3;

    // ── Consumer 配置 ──
    private int maxReconsumeTimes = 16;
    /** Consumer Group（框架自建 consumer 时使用，全局唯一，禁止与 topic 同名以免 rebalance 抢队列） */
    private String consumerGroup = "PRAGMATIC_DDD_RMQ_CONSUMER";

    // ── setters（保持链式返回 this）──

    public RocketMqConfig setNameServer(String nameServer) { this.nameServer = nameServer; return this; }

    public RocketMqConfig setProxyAddr(String proxyAddr) { this.proxyAddr = proxyAddr; return this; }

    public RocketMqConfig setRetryTimesWhenSendFailed(int retryTimesWhenSendFailed) { this.retryTimesWhenSendFailed = retryTimesWhenSendFailed; return this; }

    public RocketMqConfig setSendMsgTimeout(int sendMsgTimeout) { this.sendMsgTimeout = sendMsgTimeout; return this; }

    /** @return 消息体压缩阈值，超过此大小触发压缩 */
    public RocketMqConfig setCompressMsgBodyOverHowmuch(int compressMsgBodyOverHowmuch) { this.compressMsgBodyOverHowmuch = compressMsgBodyOverHowmuch; return this; }

    public RocketMqConfig setProducerGroup(String producerGroup) { this.producerGroup = producerGroup; return this; }

    public RocketMqConfig setDefaultDelayLevel(int defaultDelayLevel) { this.defaultDelayLevel = defaultDelayLevel; return this; }

    public RocketMqConfig setMaxReconsumeTimes(int maxReconsumeTimes) { this.maxReconsumeTimes = maxReconsumeTimes; return this; }

    public RocketMqConfig setConsumerGroup(String consumerGroup) { this.consumerGroup = consumerGroup; return this; }

    /**
     * 从配置源按 {@code rocketmq} 前缀绑定统一配置（兼容并收敛既有配置）。
     * 键约定：rocketmq.name-server / rocketmq.proxy-addr / rocketmq.retry-times-when-send-failed
     * / rocketmq.send-msg-timeout / rocketmq.compress-msg-body-over-howmuch / rocketmq.producer-group
     * / rocketmq.default-delay-level / rocketmq.max-reconsume-times。
     *
     * @param source 配置源
     * @return 绑定后的配置
     */
    public static RocketMqConfig bind(IConfigurationSource source) {
        return ConfigurationBinder.bind(source, "rocketmq", RocketMqConfig.class);
    }
}
