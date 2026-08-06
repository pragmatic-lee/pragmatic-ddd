package io.pragmatic.ddd.event.local;

import io.pragmatic.ddd.config.ConfigurationBinder;
import io.pragmatic.ddd.config.IConfigurationSource;

/**
 * 本地事件管理器的运行配置（不可变）。
 * 将线程池与重试相关参数收敛为类型化配置，可由配置源按 {@code event.local} 前缀绑定。
 *
 * @author wizard-lee
 */
public record LocalEventManagerConfig(
        int schedulerThreads,
        int corePoolSize,
        int maxPoolSize,
        int queueCapacity,
        long keepAliveSeconds,
        int deliveryDelayMs,
        int maxRetryTimes,
        int retryDelayMs) {

    /** 内置默认配置。 */
    public static LocalEventManagerConfig defaultConfig() {
        int processors = Runtime.getRuntime().availableProcessors();
        return new LocalEventManagerConfig(
                2,
                Math.max(4, processors),
                Math.max(8, processors * 2),
                1000,
                60,
                1000,
                3,
                1500);
    }

    /**
     * 从配置源按 {@code event.local} 前缀绑定配置。
     * 键约定：event.local.scheduler-threads / event.local.core-pool-size / event.local.max-pool-size /
     * event.local.queue-capacity / event.local.keep-alive-seconds / event.local.delivery-delay-ms /
     * event.local.max-retry-times / event.local.retry-delay-ms。
     *
     * @param source 配置源
     * @return 绑定后的配置
     */
    public static LocalEventManagerConfig bind(IConfigurationSource source) {
        return ConfigurationBinder.bind(source, "event.local", LocalEventManagerConfig.class, defaultConfig());
    }
}
