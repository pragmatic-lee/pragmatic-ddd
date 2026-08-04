package io.pragmatic.ddd.event.spi;

import io.pragmatic.ddd.event.internal.defaults.NoOpEventMetrics;
import org.junit.jupiter.api.Test;

/**
 * 验证空实现指标埋点方法均为无副作用的 no-op。
 *
 * @author wizard-lee
 */
class NoOpEventMetricsTest {

    private final NoOpEventMetrics metrics = new NoOpEventMetrics();

    @Test
    void recordPublish_doesNotThrow() {
        metrics.recordPublish("topic", "TestDomainEvent", true, 1L);
    }

    @Test
    void recordConsume_doesNotThrow() {
        metrics.recordConsume("topic", "sub-a", true, 1);
    }

    @Test
    void recordDlq_doesNotThrow() {
        metrics.recordDlq("topic", "reason");
    }
}
