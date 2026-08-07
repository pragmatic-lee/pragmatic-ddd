package io.pragmatic.ddd.repository.reconciliation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 ReconciliationStatus 四种一致性状态的枚举定义。
 *
 * @author wizard-lee
 */
class ReconciliationStatusTest {

    @Test
    void values_areTheFourDefinedStates() {
        assertThat(ReconciliationStatus.values()).containsExactly(
                ReconciliationStatus.CONSISTENT,
                ReconciliationStatus.STALE,
                ReconciliationStatus.ORPHAN,
                ReconciliationStatus.UNTRACKED);
    }

    @Test
    void stale_meansReadBehindWrite() {
        assertThat(ReconciliationStatus.STALE).isNotEqualTo(ReconciliationStatus.CONSISTENT);
    }
}
