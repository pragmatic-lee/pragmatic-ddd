package io.pragmatic.ddd.repository;

import io.pragmatic.ddd.repository.fixture.HeteroSampleAggregate;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IRepository#save 三路路由测试：删除 / 新建 / 更新，以及清理后的二次保存。
 *
 * @author wizard-lee
 */
class SaveRoutingTest {

    static class CountingRepository extends AbstractRepository<Long, HeteroSampleAggregate> {

        private final AtomicInteger insertCount = new AtomicInteger();
        private final AtomicInteger updateCount = new AtomicInteger();
        private final AtomicInteger removeCount = new AtomicInteger();
        private final AtomicInteger eventsSeenInDoRemove = new AtomicInteger(-1);

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
            eventsSeenInDoRemove.set(aggregateRoot.getDomainEvents().size());
        }
    }

    @Test
    void save_new_routesToInsert() {
        CountingRepository repository = new CountingRepository();
        HeteroSampleAggregate aggregate = new HeteroSampleAggregate();
        aggregate.markNew();

        repository.save(aggregate);

        assertThat(repository.insertCount.get()).isEqualTo(1);
        assertThat(repository.updateCount.get()).isZero();
        assertThat(repository.removeCount.get()).isZero();
    }

    @Test
    void save_persisted_routesToUpdate() {
        CountingRepository repository = new CountingRepository();
        HeteroSampleAggregate aggregate = new HeteroSampleAggregate();

        repository.save(aggregate);

        assertThat(repository.updateCount.get()).isEqualTo(1);
        assertThat(repository.insertCount.get()).isZero();
        assertThat(repository.removeCount.get()).isZero();
    }

    @Test
    void save_entityDelete_routesToRemove() {
        CountingRepository repository = new CountingRepository();
        HeteroSampleAggregate aggregate = new HeteroSampleAggregate();
        aggregate.markEntityDelete();

        repository.save(aggregate);

        assertThat(repository.removeCount.get()).isEqualTo(1);
        assertThat(repository.insertCount.get()).isZero();
        assertThat(repository.updateCount.get()).isZero();
    }

    @Test
    void save_deleteAndNew_removeWins() {
        CountingRepository repository = new CountingRepository();
        HeteroSampleAggregate aggregate = new HeteroSampleAggregate();
        aggregate.markNew();
        aggregate.markEntityDelete();

        repository.save(aggregate);

        assertThat(repository.removeCount.get()).isEqualTo(1);
        assertThat(repository.insertCount.get()).isZero();
        assertThat(repository.updateCount.get()).isZero();
    }

    @Test
    void save_entityDelete_triggersHookBeforeDoRemove() {
        CountingRepository repository = new CountingRepository();
        HeteroSampleAggregate aggregate = new HeteroSampleAggregate();
        aggregate.markEntityDelete();

        repository.save(aggregate);

        assertThat(repository.eventsSeenInDoRemove.get()).isEqualTo(1);
    }

    @Test
    void save_afterClear_routesToUpdate() {
        CountingRepository repository = new CountingRepository();
        HeteroSampleAggregate aggregate = new HeteroSampleAggregate();
        aggregate.markEntityDelete();
        repository.save(aggregate);

        aggregate.clearWorkUnitState();
        aggregate.recordSampleOperation();
        repository.save(aggregate);

        assertThat(repository.removeCount.get()).isEqualTo(1);
        assertThat(repository.updateCount.get()).isEqualTo(1);
        assertThat(repository.insertCount.get()).isZero();
    }

    @Test
    void save_afterClear_newAggregate_routesToInsert() {
        CountingRepository repository = new CountingRepository();
        HeteroSampleAggregate aggregate = new HeteroSampleAggregate();
        aggregate.markNew();
        aggregate.markEntityDelete();
        repository.save(aggregate);

        aggregate.clearWorkUnitState();
        aggregate.recordSampleOperation();
        repository.save(aggregate);

        assertThat(repository.removeCount.get()).isEqualTo(1);
        assertThat(repository.insertCount.get()).isEqualTo(1);
        assertThat(repository.updateCount.get()).isZero();
    }
}
