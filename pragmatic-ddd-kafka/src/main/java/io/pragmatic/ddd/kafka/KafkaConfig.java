package io.pragmatic.ddd.kafka;

import java.util.Arrays;
import java.util.OptionalInt;

/**
 * Kafka 事件管理器配置，承载 {@code kafka.*} 前缀下的外部配置。
 * record 不可声明默认值，缺失字段由配置绑定以类型零值保留；代码中以空串兜底处理可选属性。
 *
 * <p>支持的配置键（{@code kafka.} 前缀，kebab-case）：
 * <ul>
 *     <li>bootstrap-servers：broker 地址</li>
 *     <li>group：消费者组 group.id，同组多实例自动分区负载均衡</li>
 *     <li>client-id：可选 client.id</li>
 *     <li>max-poll-records：单次 poll 最大拉取条数</li>
 *     <li>poll-timeout-ms：consumer.poll 超时（毫秒）</li>
 *     <li>auto-offset-reset：无初始 offset 时的重置策略，latest / earliest</li>
 *     <li>ack：生产者 acks（默认 all）</li>
 *     <li>compression-type：生产者压缩类型，可选（空则不设置）</li>
 *     <li>enable-idempotence：生产者幂等，默认 true</li>
 *     <li>dlq-suffix：死信 topic 后缀</li>
 *     <li>max-reconsume：单条消息最大重试次数（不含首投），超过转 DLQ，默认 16（对齐 RocketMQ maxReconsumeTimes）</li>
 *     <li>concurrency：单进程内并发 consumer 线程数（多个独立 KafkaConsumer 同 group），默认 1</li>
 *     <li>send-timeout-ms：发布 / DLQ 转发 / 延时与重试转存的有界等待（毫秒，默认 10000）</li>
 *     <li>delayed-policy：DELAYED 策略，合法取值 immediate | reject | relay（默认 immediate）</li>
 *     <li>default-delay-seconds：relay 模式下的延时秒数（默认 10，对齐 RocketMQ defaultDelayLevel=3）</li>
 *     <li>delay-topic-suffix：relay 模式下延时 topic 后缀，{businessTopic}{suffix}（默认 -delay）</li>
 *     <li>inline-retry-intervals-ms：内联重试间隔表（逗号分隔毫秒，默认 100,500），档位数即每轮内联重试次数</li>
 *     <li>retry-topic-suffix：重试 topic 后缀，{businessTopic}{suffix}（默认 -retry）；置空 = 关闭重试通道，内联耗尽即进死信</li>
 *     <li>retry-hop-backoff-ms：retry topic 每轮往返的退避表（逗号分隔毫秒，默认 8 档），超出末档复用末档</li>
 *     <li>batch-budget-ms：单批处理时间预算（毫秒，默认 30000），超预算剩余消息留待下轮，避免消费线程长时间不 poll</li>
 * </ul>
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
         * 单条消息最大重试次数（不含首投），超过转 DLQ
         */
        int maxReconsume,

        /*
         * 单进程内并发 consumer 线程数（多个独立 KafkaConsumer 同 group），默认 1
         */
        int concurrency,

        /*
         * 发布 / 转存 / 转发死信的有界等待（毫秒），避免 send().get() 无界阻塞
         */
        int sendTimeoutMs,

        /*
         * DELAYED 策略：immediate（降级即时发送）/ reject（拒绝发送）/ relay（延时 topic 转存 + 到期回投）
         */
        String delayedPolicy,

        /*
         * relay 模式下的延时秒数
         */
        int defaultDelaySeconds,

        /*
         * relay 模式下延时 topic 后缀：{businessTopic}{suffix}
         */
        String delayTopicSuffix,

        /*
         * 内联重试间隔表（逗号分隔毫秒），档位数即每轮内联重试次数
         */
        String inlineRetryIntervalsMs,

        /*
         * 重试 topic 后缀：{businessTopic}{suffix}；置空表示关闭重试通道
         */
        String retryTopicSuffix,

        /*
         * retry topic 每轮往返的退避表（逗号分隔毫秒），超出末档复用末档
         */
        String retryHopBackoffMs,

        /*
         * 单批处理时间预算（毫秒），超预算的剩余消息留待下轮
         */
        int batchBudgetMs) {

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
                500,
                1000,
                "latest",
                "all",
                "",
                true,
                "-dlq",
                16,
                1,
                10000,
                "immediate",
                10,
                "-delay",
                "100,500",
                "-retry",
                "1000,5000,10000,30000,60000,120000,180000,240000",
                30000);
    }

    /**
     * 单条消息最大消费尝试次数 = 最大重试次数 + 首投 1 次；达到该次数即转死信。
     *
     * @return 最大消费尝试次数，{@code maxReconsume <= 0} 时为 1
     */
    public int attemptLimit() {
        return Math.max(maxReconsume, 0) + 1;
    }

    /**
     * 每轮内联重试次数 = 内联重试间隔表的档位数。
     *
     * @return 每轮内联重试次数，表为空或非法时为 0（失败即转存）
     */
    public int inlineRetryTimes() {
        return inlineRetryIntervalTable().length;
    }

    /**
     * 每轮消费尝试次数 = 首试 1 次 + 每轮内联重试次数，恒大于等于 1。
     *
     * @return 每轮消费尝试次数
     */
    public int roundAttempts() {
        return inlineRetryTimes() + 1;
    }

    /**
     * 取内联重试间隔表（解析后的毫秒档位）。
     *
     * @return 间隔档位数组，表为空或非法时为空数组
     */
    public int[] inlineRetryIntervalTable() {
        return parseMsTable(inlineRetryIntervalsMs);
    }

    /**
     * 取重试 topic 退避表（解析后的毫秒档位）。
     *
     * @return 退避档位数组，表为空或非法时为空数组
     */
    public int[] retryHopBackoffTable() {
        return parseMsTable(retryHopBackoffMs);
    }

    /**
     * 取第 times 次消费失败后的内联重试间隔：按轮内位置取档，保证每轮都从第 1 档开始。
     *
     * @param times 累计消费失败次数
     * @return 内联重试间隔（毫秒），表为空时为 0
     */
    public long inlineRetryBackoffMs(int times) {
        return takeBackoffMs(inlineRetryIntervalTable(), (times - 1) % roundAttempts());
    }

    /**
     * 取第 hop 轮 retry topic 往返的退避：下标 hop - 1，超出末档复用末档。
     *
     * @param hop retry topic 往返轮次（1-based）
     * @return 退避时长（毫秒），表为空时为 0
     */
    public long retryHopBackoffMs(int hop) {
        return takeBackoffMs(retryHopBackoffTable(), hop - 1);
    }

    /**
     * 取表中指定下标的退避档位：下标为负取首档，超出末档取末档。
     */
    private long takeBackoffMs(int[] table, int index) {
        if (table.length == 0) {
            return 0;
        }
        if (index < 0) {
            return table[0];
        }
        if (index >= table.length) {
            return table[table.length - 1];
        }
        return table[index];
    }

    /**
     * 解析逗号分隔的毫秒表：剔除空项、非数字项与非正值；全部非法时返回空数组。
     *
     * @param raw 原始配置值
     * @return 毫秒档位数组
     */
    static int[] parseMsTable(String raw) {
        if (raw == null || raw.isBlank()) {
            return new int[0];
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .map(KafkaConfig::parseMs)
                .filter(OptionalInt::isPresent)
                .mapToInt(OptionalInt::getAsInt)
                .filter(ms -> ms > 0)
                .toArray();
    }

    private static OptionalInt parseMs(String item) {
        try {
            return OptionalInt.of(Integer.parseInt(item));
        } catch (NumberFormatException e) {
            return OptionalInt.empty();
        }
    }
}
