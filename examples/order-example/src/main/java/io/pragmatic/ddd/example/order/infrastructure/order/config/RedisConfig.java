package io.pragmatic.ddd.example.order.infrastructure.order.config;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Optional;

/**
 * 订单示例的 Redis 客户端配置。
 * 基于 Lettuce（io.lettuce:lettuce-core）原生客户端，构造 RedisClient -> StatefulRedisConnection
 * 两层对象，不依赖 Spring Data Redis 的 RedisTemplate 自动装配（本项目所有 Bean 均手写提供）。
 *
 * <p>连接地址、密码、超时参数均从外部化配置读取（application.properties 或环境变量），不在代码中硬编码。
 * 缓存 value 以 String 承载，由 fastjson2 序列化投影对象为 JSON 字符串后写入。</p>
 *
 * @author wizard-lee
 */
@Configuration
public class RedisConfig {

    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 6379;
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(3);

    @Value("${redis.host:" + DEFAULT_HOST + "}")
    private String host;

    @Value("${redis.port:" + DEFAULT_PORT + "}")
    private int port;

    @Value("${redis.password:}")
    private String password;

    @Value("${redis.database:0}")
    private int database;

    @Value("${redis.timeout-seconds:3}")
    private long timeoutSeconds;

    /**
     * 构建低层 RedisClient（基于 Netty），负责与 Redis 节点的实际通信，线程安全、可复用。
     *
     * @return RedisClient 实例
     */
    @Bean(destroyMethod = "shutdown")
    public RedisClient redisClient() {
        RedisURI redisUri = buildRedisUri();
        return RedisClient.create(redisUri);
    }

    /**
     * 构建 StatefulRedisConnection（String 编码），用于获取同步命令对象写入/读取缓存投影。
     * Lettuce 连接线程安全，单连接即可支撑并发读写。
     *
     * @param redisClient RedisClient 实例
     * @return StatefulRedisConnection 实例
     */
    @Bean(destroyMethod = "close")
    public StatefulRedisConnection<String, String> redisConnection(RedisClient redisClient) {
        return redisClient.connect();
    }

    /**
     * 暴露同步命令对象，供 OrderRedisSource / OrderRedisByIdSearcher 注入使用。
     * 命令门面不持有底层资源，生命周期随 StatefulRedisConnection 关闭而失效，无需单独销毁。
     *
     * @param connection StatefulRedisConnection 实例
     * @return RedisCommands 同步命令
     */
    @Bean
    public RedisCommands<String, String> redisCommands(StatefulRedisConnection<String, String> connection) {
        return connection.sync();
    }

    private RedisURI buildRedisUri() {
        RedisURI.Builder builder = RedisURI.builder()
            .withHost(host)
            .withPort(port)
            .withDatabase(database)
            .withTimeout(Duration.ofSeconds(timeoutSeconds));

        password().ifPresent(builder::withPassword);

        return builder.build();
    }

    private Optional<String> password() {
        if (!StringUtils.hasText(password)) {
            return Optional.empty();
        }
        return Optional.of(password);
    }
}
