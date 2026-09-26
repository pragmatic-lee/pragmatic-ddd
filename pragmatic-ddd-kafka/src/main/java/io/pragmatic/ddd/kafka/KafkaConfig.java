package io.pragmatic.ddd.kafka;

/**
 * Kafka 事件管理器配置，承载 {@code kafka.*} 前缀下的外部配置。
 * record 不可声明默认值，缺失字段由配置绑定以类型零值保留；代码中以空串兜底处理可选属性。
 *
 * @author wizard-lee
 */
public record KafkaConfig(

        /*
         * Kafka broker 地址，对应 bootstrap.servers
         */
        String bootstrapServers,

        /*
         * 消费者组 group.id，同组多实例自动分区负载均衡
         */
        String group,

        /*
         * client.id，可选
         */
        String clientId,

        /*
         * 是否自动提交 offset，实现强制覆盖为 false 以手动提交
         */
        boolean enableAutoCommit,

        /*
         * 单次 poll 最大拉取条数
         */
        int maxPollRecords,

        /*
         * consumer.poll 超时（毫秒）
         */
        int pollTimeoutMs,

        /*
         * 无初始 offset 时的重置策略，latest / earliest
         */
        String autoOffsetReset,

        /*
         * 生产者 acks，默认 all
         */
        String ack,

        /*
         * 生产者压缩类型，可选（空则不设置）
         */
        String compressionType,

        /*
         * 生产者幂等，默认 true
         */
        boolean enableIdempotence,

        /*
         * 死信 topic 后缀
         */
        String dlqSuffix,

        /*
         * 单条消息最大重投次数，超过转 DLQ
         */
        int maxReconsume,

        /*
         * 单进程内并发 consumer 线程数（多个独立 KafkaConsumer 同 group），默认 1
         */
        int concurrency) {

    /**
     * 全参构造之上的便捷工厂，提供领域事件订阅的默认取值。
     *
     * @param bootstrapServers broker 地址
     * @param group            消费者组
     * @return 采用默认值的 KafkaConfig
     */
    public static KafkaConfig withDefaults(String bootstrapServers, String group) {
        return new KafkaConfig(
                bootstrapServers,
                group,
                "",
                false,
                500,
                1000,
                "latest",
                "all",
                "",
                true,
                "-dlq",
                3,
                1);
    }
}
