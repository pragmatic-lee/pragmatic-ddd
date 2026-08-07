package io.pragmatic.ddd.rocketmq;

import io.pragmatic.ddd.event.internal.defaults.ConfigurableTopicResolver;
import io.pragmatic.ddd.event.internal.defaults.SubscriberOrderManager;
import io.pragmatic.ddd.event.spi.ISubscriberOrderManager;
import io.pragmatic.ddd.event.spi.ITopicResolver;

import java.net.Socket;

/**
 * RocketMQ 集成测试支撑类，集中管理地址、可用性探测与 Manager 构建。
 * <p>
 * 地址通过系统属性注入，不在测试代码中硬编码 localhost；缺 broker 时
 * {@code is4xAvailable()}/{@code is5xAvailable()} 返回 false，使集成测试整类跳过，
 * 保证无 RocketMQ 环境构建仍成功。
 *
 * @author wizard-lee
 */
final class RocketMqTestSupport {

    /** 4.x NameServer 地址（Remoting 协议）。 */
    static String nameServer() {
        return System.getProperty("rocketmq.name-server", "localhost:9876");
    }

    /** 5.x gRPC Proxy 地址。 */
    static String proxyAddr() {
        return System.getProperty("rocketmq.proxy-addr", "localhost:8081");
    }

    /** 是否显式跳过 4.x 集成。 */
    static boolean skip4x() {
        return Boolean.getBoolean("rocketmq.skip-4x");
    }

    /** 是否显式跳过 5.x 集成。 */
    static boolean skip5x() {
        return Boolean.getBoolean("rocketmq.skip-5x");
    }

    /** 4.x NameServer 端口是否可达（短超时探测），或显式 skip 时返回 false。 */
    static boolean is4xAvailable() {
        if (skip4x()) {
            return false;
        }
        return isPortReachable(nameServer());
    }

    /** 5.x gRPC Proxy 端口是否可达（短超时探测），或显式 skip 时返回 false。 */
    static boolean is5xAvailable() {
        if (skip5x()) {
            return false;
        }
        return isPortReachable(proxyAddr());
    }

    /** 解析 host:port 并做短超时 TCP 探测。 */
    private static boolean isPortReachable(String address) {
        String[] parts = address.split(":");
        if (parts.length != 2) {
            return false;
        }
        String host = parts[0];
        int port;
        try {
            port = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return false;
        }
        try (Socket socket = new Socket()) {
            socket.connect(new java.net.InetSocketAddress(host, port), 1500);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 创建 4.x 事件管理器（使用类名作为默认 topic 的解析器）。 */
    static RocketMqEventManager create4xManager(String defaultTopic) {
        return create4xManager(defaultTopic, null);
    }

    /** 创建 4.x 事件管理器（可注入顺序管理器）。 */
    static RocketMqEventManager create4xManager(String defaultTopic, ISubscriberOrderManager orderManager) {
        RocketMqEventManager.Builder builder = RocketMqEventManager.builder()
                .config(new RocketMqConfig().setNameServer(nameServer())
                        .setConsumerGroup("pdd-ddd-test-4x"))
                .topicResolver(ConfigurableTopicResolver.builder().globalDefaultTopic(defaultTopic).build())
                .serializer(new Fastjson2EventSerializer());
        if (orderManager != null) {
            builder.orderManager(orderManager);
        }
        return builder.build();
    }

    /** 创建 5.x 事件管理器（使用类名作为默认 topic 的解析器）。 */
    static RocketMqGrpcEventManager create5xManager(String defaultTopic) {
        return create5xManager(defaultTopic, null);
    }

    /** 创建 5.x 事件管理器（可注入顺序管理器）。 */
    static RocketMqGrpcEventManager create5xManager(String defaultTopic, ISubscriberOrderManager orderManager) {
        RocketMqGrpcEventManager.Builder builder = RocketMqGrpcEventManager.builder()
                .config(new RocketMqConfig().setProxyAddr(proxyAddr())
                        .setConsumerGroup("pdd-ddd-test-5x"))
                .topicResolver(ConfigurableTopicResolver.builder().globalDefaultTopic(defaultTopic).build())
                .serializer(new Fastjson2EventSerializer());
        if (orderManager != null) {
            builder.orderManager(orderManager);
        }
        return builder.build();
    }

    /** 构建仅含全局默认 topic 的解析器（模式 A：所有 Event 走同一底层 topic）。 */
    static ITopicResolver singleTopicResolver(String topic) {
        return ConfigurableTopicResolver.builder().globalDefaultTopic(topic).build();
    }

    /** 构建按事件类型分 topic 的解析器（模式 B）。 */
    static ITopicResolver perEventTypeResolver(String defaultTopic, String eventA, String topicA, String eventB, String topicB) {
        return ConfigurableTopicResolver.builder()
                .globalDefaultTopic(defaultTopic)
                .eventTopic(eventA, topicA)
                .eventTopic(eventB, topicB)
                .build();
    }

    /** 构建按订阅者分 topic 的解析器（模式 C）。 */
    static ITopicResolver perSubscriberResolver(String defaultTopic, String event, String subscriber, String topic) {
        return ConfigurableTopicResolver.builder()
                .globalDefaultTopic(defaultTopic)
                .subscriberTopic(event, subscriber, topic)
                .build();
    }

    /** 探测用默认顺序管理器。 */
    static ISubscriberOrderManager orderManager() {
        return new SubscriberOrderManager();
    }
}
