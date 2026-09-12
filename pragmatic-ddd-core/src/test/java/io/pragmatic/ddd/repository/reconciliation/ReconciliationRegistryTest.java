package io.pragmatic.ddd.repository.reconciliation;

import io.pragmatic.ddd.repository.ReplicaKey;
import io.pragmatic.ddd.repository.reconciliation.fixture.StubAggregate;
import io.pragmatic.ddd.repository.reconciliation.fixture.StubReplica;
import io.pragmatic.ddd.repository.reconciliation.fixture.StubRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReconciliationRegistryTest {

    private static final ReplicaKey KEY = new ReplicaKey(StubAggregate.class, "es:stub");

    @Test
    void registerAndResolve_replicaAndRepository() {
        ReconciliationRegistry registry = new ReconciliationRegistry();
        StubReplica replica = new StubReplica(KEY, 1L);
        registry.registerReplica(replica);
        registry.registerRepository(StubAggregate.class, new StubRepository(java.util.Map.of()));

        assertThat(registry.replicaFor(KEY)).isSameAs(replica);
        assertThat(registry.repositoryFor(StubAggregate.class)).isInstanceOf(StubRepository.class);
    }

    @Test
    void replicaKeysOf_returnsKeysForAggregateType() {
        ReconciliationRegistry registry = new ReconciliationRegistry();
        ReplicaKey other = new ReplicaKey(StubAggregate.class, "redis:stub");
        registry.registerReplica(new StubReplica(KEY, 1L));
        registry.registerReplica(new StubReplica(other, 1L));

        assertThat(registry.replicaKeysOf(StubAggregate.class))
                .containsExactlyInAnyOrder(KEY, other);
    }

    @Test
    void replicaKeysOf_unknownType_returnsEmpty() {
        ReconciliationRegistry registry = new ReconciliationRegistry();
        assertThat(registry.replicaKeysOf(StubAggregate.class)).isEmpty();
    }

    @Test
    void registerReplica_duplicateKeyDifferentInstance_throws() {
        ReconciliationRegistry registry = new ReconciliationRegistry();
        registry.registerReplica(new StubReplica(KEY, 1L));

        assertThatThrownBy(() -> registry.registerReplica(new StubReplica(KEY, 2L)))
                .isInstanceOf(ReconcileDuplicateReplicaException.class);
    }

    @Test
    void registerReplica_sameInstanceTwice_isIdempotent() {
        ReconciliationRegistry registry = new ReconciliationRegistry();
        StubReplica replica = new StubReplica(KEY, 1L);
        registry.registerReplica(replica);
        registry.registerReplica(replica);

        assertThat(registry.replicaFor(KEY)).isSameAs(replica);
    }

    @Test
    void replicaFor_unregistered_throwsNotFound() {
        ReconciliationRegistry registry = new ReconciliationRegistry();

        assertThatThrownBy(() -> registry.replicaFor(KEY))
                .isInstanceOf(ReplicaNotFoundException.class)
                .hasMessageContaining(KEY.replicaId());
    }
}
