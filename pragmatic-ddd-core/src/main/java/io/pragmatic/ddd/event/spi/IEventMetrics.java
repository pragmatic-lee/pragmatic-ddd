package io.pragmatic.ddd.event.spi;

/**
 * 事件监控 SPI，通用接口，不绑定具体 MQ 类型。
 *
 * @author wizard-lee
 */
public interface IEventMetrics {

    /** 记录事件发布 */
    void recordPublish(String destination, String eventType, boolean success, long latencyMs);

    /** 记录事件消费 */
    void recordConsume(String destination, String subscriber, boolean success, int attemptCount);

    /** 记录死信投递 */
    void recordDlq(String destination, String reason);
}
