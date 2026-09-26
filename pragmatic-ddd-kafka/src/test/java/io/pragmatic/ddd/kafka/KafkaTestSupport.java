package io.pragmatic.ddd.kafka;

import io.pragmatic.ddd.event.internal.defaults.ConfigurableTopicResolver;
import io.pragmatic.ddd.event.spi.ITopicResolver;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Kafka 集成测试支撑类，结构与 RocketMQ 的 {@code RocketMqTestSupport} 对齐。
 * <p>
 * 仅当本机（或容器）存在可达的 Kafka 时，集成测试才会真正执行；
 * 否则测试通过 {@code Assumptions} 静默跳过，不影响无 broker 环境的构建。
 *
 * @author wizard-lee
 */
public final class KafkaTestSupport {

    private static final String DEFAULT_BOOTSTRAP = "localhost:9092";
    private static final String SYS_KEY = "kafka.bootstrap-servers";

    private KafkaTestSupport() {
    }

    /**
     * 返回 Kafka bootstrap 地址，可通过 {@code -Dkafka.bootstrap-servers=host:port} 覆盖。
     */
    public static String bootstrapServers() {
        String value = System.getProperty(SYS_KEY);
        if (value != null && !value.isBlank()) {
            return value;
        }
        return DEFAULT_BOOTSTRAP;
    }

    /**
     * 通过 TCP 端口探测判断 Kafka 是否可达；不可达时集成测试应跳过。
     */
    public static boolean isAvailable() {
        String bs = bootstrapServers();
        int lastColon = bs.lastIndexOf(':');
        String host = lastColon < 0 ? "localhost" : bs.substring(0, lastColon);
        int port = lastColon < 0 ? 9092 : Integer.parseInt(bs.substring(lastColon + 1));
        try (java.net.Socket socket = new java.net.Socket()) {
            socket.connect(new java.net.InetSocketAddress(host, port), 800);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 使用全局默认主题构建一个连接到真实 broker 的 {@link KafkaEventManager}。
     * 为了规避「生产先于分区分配完成」的竞态，消费位移策略固定为 earliest。
     */
    public static KafkaEventManager createManager(String defaultTopic, String group) {
        return createManager(
                ConfigurableTopicResolver.builder().globalDefaultTopic(defaultTopic).build(),
                group);
    }

    /**
     * 使用自定义主题解析器构建一个连接到真实 broker 的 {@link KafkaEventManager}。
     */
    public static KafkaEventManager createManager(ITopicResolver resolver, String group) {
        return createManager(resolver, testConfig(group));
    }

    /**
     * 使用自定义主题解析器与配置构建一个连接到真实 broker 的 {@link KafkaEventManager}。
     */
    public static KafkaEventManager createManager(ITopicResolver resolver, KafkaConfig config) {
        return KafkaEventManager.builder(config, resolver)
                .serializer(new KafkaEventSerializer())
                .build();
    }

    /**
     * 集成测试基线配置：重试窗口收窄（内联 50ms ×2、max-reconsume=2）且默认关闭重试通道，缩短用例时长。
     *
     * @param group 消费者组
     * @return 集成测试用 Kafka 配置
     */
    public static KafkaConfig testConfig(String group) {
        return testConfig(group, 2, "", "100,100");
    }

    /**
     * 集成测试配置：可独立指定重试窗口与重试通道，其余取测试基线。
     *
     * @param group            消费者组
     * @param maxReconsume     最大重试次数（不含首投）
     * @param retryTopicSuffix 重试 topic 后缀，空串表示关闭重试通道
     * @param retryHopBackoffMs 重试 topic 退避表
     * @return 集成测试用 Kafka 配置
     */
    public static KafkaConfig testConfig(String group,
                                         int maxReconsume,
                                         String retryTopicSuffix,
                                         String retryHopBackoffMs) {
        return new KafkaConfig(
                bootstrapServers(),
                group,
                "",
                500,
                1000,
                "earliest",
                "all",
                "",
                true,
                "-dlq",
                maxReconsume,
                1,
                10000,
                "immediate",
                10,
                "-delay",
                "50,50",
                retryTopicSuffix,
                retryHopBackoffMs,
                30000);
    }

    /**
     * 延时通道集成测试配置：delayed-policy=relay，延时事件转存 {topic}-delay 并在 defaultDelaySeconds 秒后回投业务 topic；
     * 重试通道关闭，避免两链路互相干扰。
     *
     * @param group               消费者组
     * @param defaultDelaySeconds 延时时长（秒）
     * @return 集成测试用 Kafka 配置
     */
    public static KafkaConfig testDelayConfig(String group, int defaultDelaySeconds) {
        return new KafkaConfig(
                bootstrapServers(),
                group,
                "",
                500,
                1000,
                "earliest",
                "all",
                "",
                true,
                "-dlq",
                2,
                1,
                10000,
                "relay",
                defaultDelaySeconds,
                "-delay",
                "50,50",
                "",
                "100,100",
                30000);
    }

    /**
     * 创建主题（已存在则忽略），避免依赖 broker 的自动建主题开关。
     */
    public static void createTopic(String topic) {
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers());
        props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 10_000);
        props.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, 10_000);
        try (AdminClient admin = AdminClient.create(props)) {
            NewTopic newTopic = new NewTopic(topic, 1, (short) 1);
            admin.createTopics(Collections.singletonList(newTopic))
                    .all()
                    .get(10, TimeUnit.SECONDS);
        } catch (TopicExistsException ex) {
            return;
        } catch (Exception ex) {
            // 主题创建失败（如 broker 未开启自动建主题或 AdminClient 超时）时降级为尽力而为：
            // 依赖 broker 的 auto.create.topics.enable=true，由真实发送动作触发建主题。
            System.out.println("[KafkaTestSupport] 预建主题 " + topic + " 失败，将依赖 broker 自动建主题: "
                    + ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
    }

    /**
     * 创建一个独立的原生消费者，用于校验死信队列等真实 broker 上的产物。
     */
    public static KafkaConsumer<String, byte[]> createRawConsumer(String group) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, group);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        return new KafkaConsumer<>(props);
    }
}
