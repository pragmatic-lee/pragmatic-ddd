package io.pragmatic.ddd.event.internal.defaults;

import io.pragmatic.ddd.event.spi.IEventMetrics;

/**
 * 默认 NOP 实现，所有指标采集为空操作。
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
