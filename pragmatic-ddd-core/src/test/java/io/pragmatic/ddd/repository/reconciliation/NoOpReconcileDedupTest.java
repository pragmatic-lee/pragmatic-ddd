package io.pragmatic.ddd.repository.reconciliation;

import io.pragmatic.ddd.repository.ReplicaKey;
import io.pragmatic.ddd.repository.reconciliation.fixture.StubAggregate;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NoOpReconcileDedupTest {

    @Test
    void shouldSkip_alwaysFalse() {
        ReplicaKey key = new ReplicaKey(StubAggregate.class, "es:x");
        assertThat(NoOpReconcileDedup.INSTANCE.shouldSkip(key, 1L)).isFalse();
    }

    @Test
    void mark_isNoOp() {
        ReplicaKey key = new ReplicaKey(StubAggregate.class, "es:x");
        NoOpReconcileDedup.INSTANCE.mark(key, 1L); // 不应抛异常
    }
}
