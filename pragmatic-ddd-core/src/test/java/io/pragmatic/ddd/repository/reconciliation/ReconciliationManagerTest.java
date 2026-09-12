package io.pragmatic.ddd.repository.reconciliation;

import io.pragmatic.ddd.repository.ReplicaKey;
import io.pragmatic.ddd.repository.reconciliation.fixture.StubAggregate;
import io.pragmatic.ddd.repository.reconciliation.fixture.StubReplica;
import io.pragmatic.ddd.repository.reconciliation.fixture.StubRepository;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ReconciliationManagerTest {

    private static final ReplicaKey KEY = new ReplicaKey(StubAggregate.class, "es:stub");

    private ReconciliationRegistry newRegistry(long writeVersion, long readVersion) {
        ReconciliationRegistry registry = new ReconciliationRegistry();
        registry.registerReplica(new StubReplica(KEY, readVersion));
        registry.registerRepository(StubAggregate.class,
                new StubRepository(Map.of(1L, new StubAggregate(writeVersion))));
        return registry;
    }

    @Test
    void reconcile_stale_returnsStaleResultForReplica() {
        ReconciliationManager manager =
                new ReconciliationManager(newRegistry(5, 3), NoOpReconcileDedup.INSTANCE);
        Map<ReplicaKey, Reconciliation> results = manager.reconcile(StubAggregate.class, 1L);
        assertThat(results).containsKey(KEY);
        assertThat(results.get(KEY).isStale()).isTrue();
    }

    @Test
    void reconcile_consistent_returnsConsistentResultForReplica() {
        ReconciliationManager manager =
                new ReconciliationManager(newRegistry(5, 5), NoOpReconcileDedup.INSTANCE);
        Map<ReplicaKey, Reconciliation> results = manager.reconcile(StubAggregate.class, 1L);
        assertThat(results.get(KEY).isConsistent()).isTrue();
    }

    @Test
    void reconcile_singleReplica_passesThrough() {
        ReconciliationManager manager =
                new ReconciliationManager(newRegistry(5, 3), NoOpReconcileDedup.INSTANCE);
        Reconciliation r = manager.reconcile(KEY, 1L);
        assertThat(r.isStale()).isTrue();
    }

    @Test
    void reconcile_stale_invokesRebuild() {
        StubReplica replica = new StubReplica(KEY, 3L);
        ReconciliationRegistry registry = new ReconciliationRegistry();
        registry.registerReplica(replica);
        registry.registerRepository(StubAggregate.class,
                new StubRepository(Map.of(1L, new StubAggregate(5L))));

        new ReconciliationManager(registry, NoOpReconcileDedup.INSTANCE).reconcile(StubAggregate.class, 1L);

        assertThat(replica.lastRebuiltId).hasValue(1L);
    }

    @Test
    void reconcile_orphan_invokesPurgeOrphan() {
        StubReplica replica = new StubReplica(KEY, 3L);
        ReconciliationRegistry registry = new ReconciliationRegistry();
        registry.registerReplica(replica);
        registry.registerRepository(StubAggregate.class, new StubRepository(Map.of()));

        new ReconciliationManager(registry, NoOpReconcileDedup.INSTANCE).reconcile(StubAggregate.class, 1L);

        assertThat(replica.lastPurgedId).hasValue(1L);
        assertThat(replica.lastRebuiltId).hasValue(null);
    }

    @Test
    void reconcile_untracked_doesNotRebuild() {
        StubReplica replica = new StubReplica(KEY, -1L);
        ReconciliationRegistry registry = new ReconciliationRegistry();
        registry.registerReplica(replica);
        registry.registerRepository(StubAggregate.class,
                new StubRepository(Map.of(1L, new StubAggregate(5L))));

        Reconciliation r = new ReconciliationManager(registry, NoOpReconcileDedup.INSTANCE)
                .reconcile(StubAggregate.class, 1L)
                .get(KEY);

        assertThat(r.isUntracked()).isTrue();
        assertThat(replica.lastRebuiltId).hasValue(null);
    }
}
