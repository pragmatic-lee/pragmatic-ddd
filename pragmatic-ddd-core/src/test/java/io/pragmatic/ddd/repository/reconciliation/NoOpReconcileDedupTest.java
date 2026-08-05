package io.pragmatic.ddd.repository.reconciliation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NoOpReconcileDedupTest {

    @Test
    void instance_isSingleton() {
        assertThat(NoOpReconcileDedup.INSTANCE).isSameAs(NoOpReconcileDedup.INSTANCE);
    }

    @Test
    void shouldSkip_alwaysFalse() {
        ReconciliationTarget target = new ReconciliationTarget(StubAggregate.class, "es:x");
        assertThat(NoOpReconcileDedup.INSTANCE.shouldSkip(target, 1L)).isFalse();
    }

    @Test
    void mark_isNoOp() {
        ReconciliationTarget target = new ReconciliationTarget(StubAggregate.class, "es:x");
        NoOpReconcileDedup.INSTANCE.mark(target, 1L); // 不应抛异常
    }

    private static final class StubAggregate extends io.pragmatic.ddd.base.AggregateRoot<Long> {
        @Override
        protected io.pragmatic.ddd.base.BrokenRuleRegistry brokenRuleRegistry() {
            return null;
        }

        @Override
        protected io.pragmatic.ddd.operation.OperationRegistry operationRegistry() {
            return null;
        }
    }
}
