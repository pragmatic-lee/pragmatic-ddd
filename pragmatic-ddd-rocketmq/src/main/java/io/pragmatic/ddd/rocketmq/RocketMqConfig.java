package io.pragmatic.ddd.rocketmq;

/**
 * RocketMQ 统一配置类。
 * <p>
 * nameServer 仅在框架自建 Producer/Consumer 时需要；
 * 如果外部注入 Producer，则 nameServer 可不配。
 * proxyAddr 仅在 gRPC 实现时需要（rocketmq-client-java 可选依赖）。
 *
 * @author wizard-lee
 */
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

    // ── getters / setters ──

    public String getNameServer() { return nameServer; }
    public RocketMqConfig setNameServer(String nameServer) { this.nameServer = nameServer; return this; }

    public String getProxyAddr() { return proxyAddr; }
    public RocketMqConfig setProxyAddr(String proxyAddr) { this.proxyAddr = proxyAddr; return this; }

    public int getRetryTimesWhenSendFailed() { return retryTimesWhenSendFailed; }
    public RocketMqConfig setRetryTimesWhenSendFailed(int retryTimesWhenSendFailed) { this.retryTimesWhenSendFailed = retryTimesWhenSendFailed; return this; }

    public int getSendMsgTimeout() { return sendMsgTimeout; }
    public RocketMqConfig setSendMsgTimeout(int sendMsgTimeout) { this.sendMsgTimeout = sendMsgTimeout; return this; }

    /** @return 消息体压缩阈值，超过此大小触发压缩 */
    public int getCompressMsgBodyOverHowmuch() { return compressMsgBodyOverHowmuch; }
    public RocketMqConfig setCompressMsgBodyOverHowmuch(int compressMsgBodyOverHowmuch) { this.compressMsgBodyOverHowmuch = compressMsgBodyOverHowmuch; return this; }

    public String getProducerGroup() { return producerGroup; }
    public RocketMqConfig setProducerGroup(String producerGroup) { this.producerGroup = producerGroup; return this; }

    public int getDefaultDelayLevel() { return defaultDelayLevel; }
    public RocketMqConfig setDefaultDelayLevel(int defaultDelayLevel) { this.defaultDelayLevel = defaultDelayLevel; return this; }

    public int getMaxReconsumeTimes() { return maxReconsumeTimes; }
    public RocketMqConfig setMaxReconsumeTimes(int maxReconsumeTimes) { this.maxReconsumeTimes = maxReconsumeTimes; return this; }

    public String getConsumerGroup() { return consumerGroup; }
    public RocketMqConfig setConsumerGroup(String consumerGroup) { this.consumerGroup = consumerGroup; return this; }
}
