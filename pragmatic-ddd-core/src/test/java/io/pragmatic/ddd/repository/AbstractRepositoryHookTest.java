package io.pragmatic.ddd.repository;

import io.pragmatic.ddd.base.fixture.SampleAggregate;
import io.pragmatic.ddd.base.fixture.SampleEvent;
import io.pragmatic.ddd.operation.SampleRegistry;
import io.pragmatic.ddd.repository.fixture.HeteroSampleAggregate;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AbstractRepository 钩子触发测试：验证 insert / update / remove 落库前统一触发
 * 聚合根数据同步钩子，以及 save 经虚分发只触发一次。
 */
class AbstractRepositoryHookTest {

    static class NoHookRepository extends AbstractRepository<Long, SampleAggregate> {
        @Override
        public SampleAggregate findById(Long id) {
            return null;
        }

        @Override
        protected void doInsert(SampleAggregate aggregateRoot) {
        }

        @Override
        protected void doUpdate(SampleAggregate aggregateRoot) {
        }

        @Override
        protected void doRemove(SampleAggregate aggregateRoot) {
        }
    }

    static class HookRepository extends AbstractRepository<Long, HeteroSampleAggregate> {
        private final AtomicInteger insertCount = new AtomicInteger();
        private final AtomicInteger updateCount = new AtomicInteger();
        private final AtomicInteger removeCount = new AtomicInteger();

        @Override
        public HeteroSampleAggregate findById(Long id) {
            return null;
        }

        @Override
        protected void doInsert(HeteroSampleAggregate aggregateRoot) {
            insertCount.incrementAndGet();
        }

        @Override
        protected void doUpdate(HeteroSampleAggregate aggregateRoot) {
            updateCount.incrementAndGet();
        }

        @Override
        protected void doRemove(HeteroSampleAggregate aggregateRoot) {
            removeCount.incrementAndGet();
        }
    }

    @Test
    void noHookAggregate_save_collectsNoHeteroEvent() {
        NoHookRepository repository = new NoHookRepository();
        SampleAggregate aggregate = new SampleAggregate();
        aggregate.markNew();
        repository.save(aggregate);
        assertThat(aggregate.getDomainEvents()).isEmpty();
    }

    @Test
    void hookAggregate_insert_newState_collectsHeteroEvent() {
        HookRepository repository = new HookRepository();
        HeteroSampleAggregate aggregate = new HeteroSampleAggregate();
        aggregate.markNew();
        repository.insert(aggregate);
        List<?> events = aggregate.getDomainEvents();
        assertThat(events).hasSize(1);
        SampleEvent event = (SampleEvent) events.get(0);
        assertThat(event).isInstanceOf(SampleEvent.class);
        assertThat(event.operationCode).isEqualTo(io.pragmatic.ddd.operation.SampleRegistry.A.code());
        assertThat(event.version).isNotEqualTo(0);
        assertThat(repository.insertCount.get()).isEqualTo(1);
    }

    @Test
    void hookAggregate_update_notNew_collectsHeteroEvent() {
        HookRepository repository = new HookRepository();
        HeteroSampleAggregate aggregate = new HeteroSampleAggregate();
        repository.update(aggregate);
        assertThat(aggregate.getDomainEvents()).hasSize(1);
        assertThat(repository.updateCount.get()).isEqualTo(1);
    }

    @Test
    void hookAggregate_remove_entityDelete_collectsHeteroEvent() {
        HookRepository repository = new HookRepository();
        HeteroSampleAggregate aggregate = new HeteroSampleAggregate();
        aggregate.markEntityDelete();
        repository.remove(aggregate);
        assertThat(aggregate.getDomainEvents()).hasSize(1);
        assertThat(repository.removeCount.get()).isEqualTo(1);
    }

    @Test
    void hookAggregate_save_new_triggersHookOnce() {
        HookRepository repository = new HookRepository();
        HeteroSampleAggregate aggregate = new HeteroSampleAggregate();
        aggregate.markNew();
        repository.save(aggregate);
        assertThat(aggregate.getDomainEvents()).hasSize(1);
        assertThat(repository.insertCount.get()).isEqualTo(1);
        assertThat(repository.updateCount.get()).isEqualTo(0);
    }

    @Test
    void hookAggregate_save_notNew_invokesUpdateOnly() {
        HookRepository repository = new HookRepository();
        HeteroSampleAggregate aggregate = new HeteroSampleAggregate();
        repository.save(aggregate);
        assertThat(aggregate.getDomainEvents()).hasSize(1);
        assertThat(repository.updateCount.get()).isEqualTo(1);
        assertThat(repository.insertCount.get()).isEqualTo(0);
    }

    @Test
    void hookAggregate_remove_plainEntity_triggersHook() {
        HookRepository repository = new HookRepository();
        HeteroSampleAggregate aggregate = new HeteroSampleAggregate();
        repository.remove(aggregate);
        assertThat(aggregate.getDomainEvents()).hasSize(1);
        assertThat(repository.removeCount.get()).isEqualTo(1);
    }

    @Test
    void hookAggregate_insert_collectedEvent_hasVersionAndOperationCode() {
        HookRepository repository = new HookRepository();
        HeteroSampleAggregate aggregate = new HeteroSampleAggregate();
        aggregate.markNew();
        repository.insert(aggregate);
        List<?> events = aggregate.getDomainEvents();
        assertThat(events).hasSize(1);
        SampleEvent event = (SampleEvent) events.get(0);
        assertThat(event.operationCode).isEqualTo(io.pragmatic.ddd.operation.SampleRegistry.A.code());
        assertThat(event.version).isNotEqualTo(0);
    }
}
