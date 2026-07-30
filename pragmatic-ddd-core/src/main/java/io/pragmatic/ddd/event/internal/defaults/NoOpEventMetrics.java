package io.pragmatic.ddd.event.internal.defaults;

import io.pragmatic.ddd.event.spi.IEventMetrics;

/**
 * 默认空实现指标采集器，所有方法均为空操作。
 *
 * @author wizard-lee
 */
public class NoOpEventMetrics implements IEventMetrics {

    @Override
    public void recordPublish(String destination, String eventType, boolean success, long latencyMs) {
        // no-op
    }

    @Override
    public void recordConsume(String destination, String subscriber, boolean success, int attemptCount) {
        // no-op
    }

    @Override
    public void recordDlq(String destination, String reason) {
        // no-op
    }
}
