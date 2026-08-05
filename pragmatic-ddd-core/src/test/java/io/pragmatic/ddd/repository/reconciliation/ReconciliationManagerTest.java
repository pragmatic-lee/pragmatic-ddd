package io.pragmatic.ddd.repository.reconciliation;

import io.pragmatic.ddd.repository.reconciliation.fixture.StubAggregate;
import io.pragmatic.ddd.repository.reconciliation.fixture.StubRepository;
import io.pragmatic.ddd.repository.reconciliation.fixture.StubResolver;
import io.pragmatic.ddd.repository.reconciliation.fixture.StubResynchronizer;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ReconciliationManagerTest {

    private static final ReconciliationTarget TARGET =
            new ReconciliationTarget(StubAggregate.class, "es:stub");

    private ReconciliationRegistry newRegistry(long writeVersion, long readVersion) {
        ReconciliationRegistry registry = new ReconciliationRegistry();
        registry.registerResolver(TARGET, new StubResolver(TARGET, readVersion));
        registry.registerResynchronizer(TARGET, new StubResynchronizer(TARGET));
        registry.registerRepository(StubAggregate.class, new StubRepository(Map.of(1L, new StubAggregate(writeVersion))));
        return registry;
    }

    @Test
    void reconcile_stale_returnsStaleResultForTarget() {
        ReconciliationManager manager =
                new ReconciliationManager(newRegistry(5, 3), NoOpReconcileDedup.INSTANCE);
        Map<ReconciliationTarget, Reconciliation> results = manager.reconcile(StubAggregate.class, 1L);
        assertThat(results).containsKey(TARGET);
        assertThat(results.get(TARGET).isStale()).isTrue();
    }

    @Test
    void reconcile_consistent_returnsConsistentResultForTarget() {
        ReconciliationManager manager =
                new ReconciliationManager(newRegistry(5, 5), NoOpReconcileDedup.INSTANCE);
        Map<ReconciliationTarget, Reconciliation> results = manager.reconcile(StubAggregate.class, 1L);
        assertThat(results.get(TARGET).isConsistent()).isTrue();
    }

    @Test
    void reconcile_singleTarget_passesThrough() {
        ReconciliationManager manager =
                new ReconciliationManager(newRegistry(5, 3), NoOpReconcileDedup.INSTANCE);
        Reconciliation r = manager.reconcile(TARGET, 1L);
        assertThat(r.isStale()).isTrue();
    }
}
