package io.pragmatic.ddd.application.outbox;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OutboxStatus 状态机枚举测试：确认四态完备且命名稳定（供存储/认领逻辑依赖）。
 */
class OutboxStatusTest {

    @Test
    void statuses_coverFullStateMachine() {
        assertThat(OutboxStatus.values())
                .containsExactly(OutboxStatus.PENDING, OutboxStatus.PROCESSING,
                        OutboxStatus.SENT, OutboxStatus.FAILED);
    }

    @Test
    void statusNames_matchPersistenceConvention() {
        assertThat(OutboxStatus.PENDING.name()).isEqualTo("PENDING");
        assertThat(OutboxStatus.PROCESSING.name()).isEqualTo("PROCESSING");
        assertThat(OutboxStatus.SENT.name()).isEqualTo("SENT");
        assertThat(OutboxStatus.FAILED.name()).isEqualTo("FAILED");
    }
}
