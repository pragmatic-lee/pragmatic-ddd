package io.pragmatic.ddd.repository.reconciliation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 Reconciliation 依据读写版本计算一致性状态。
 *
 * @author wizard-lee
 */
class ReconciliationTest {

    @Test
    void of_consistent_whenReadGteWrite() {
        Reconciliation r = Reconciliation.of(5, 5);
        assertThat(r.status()).isEqualTo(ReconciliationStatus.CONSISTENT);
        assertThat(r.isConsistent()).isTrue();
    }

    @Test
    void of_stale_whenReadLtWrite() {
        Reconciliation r = Reconciliation.of(3, 5);
        assertThat(r.status()).isEqualTo(ReconciliationStatus.STALE);
        assertThat(r.isStale()).isTrue();
        assertThat(r.readVersion()).isEqualTo(3);
        assertThat(r.writeVersion()).isEqualTo(5);
    }

    @Test
    void of_orphan_whenWriteMissing() {
        Reconciliation r = Reconciliation.of(2, -1);
        assertThat(r.status()).isEqualTo(ReconciliationStatus.ORPHAN);
        assertThat(r.isOrphan()).isTrue();
    }

    @Test
    void of_untracked_whenReadMissing() {
        Reconciliation r = Reconciliation.of(-1, 5);
        assertThat(r.status()).isEqualTo(ReconciliationStatus.UNTRACKED);
        assertThat(r.isUntracked()).isTrue();
    }
}
