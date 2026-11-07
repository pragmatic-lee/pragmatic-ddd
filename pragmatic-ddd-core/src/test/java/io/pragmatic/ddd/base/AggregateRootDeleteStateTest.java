package io.pragmatic.ddd.base;

import io.pragmatic.ddd.base.fixture.SampleAggregate;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 聚合根删除标记生命周期测试：确认 clearWorkUnitState 复位删除标记，且不改动新建标记。
 *
 * @author wizard-lee
 */
class AggregateRootDeleteStateTest {

    @Test
    void entityDelete_defaultFalse() {
        SampleAggregate aggregate = new SampleAggregate();

        assertThat(aggregate.isEntityDelete()).isFalse();
    }

    @Test
    void clearWorkUnitState_resetsEntityDelete() {
        SampleAggregate aggregate = new SampleAggregate();
        aggregate.markEntityDelete();

        aggregate.clearWorkUnitState();

        assertThat(aggregate.isEntityDelete()).isFalse();
    }

    @Test
    void clearWorkUnitState_keepsIsNew() {
        SampleAggregate aggregate = new SampleAggregate();
        aggregate.markNew();

        aggregate.clearWorkUnitState();

        assertThat(aggregate.isNew()).isTrue();
    }

    @Test
    void clearWorkUnitState_whenNotDeleted_keepsEntityDeleteFalse() {
        SampleAggregate aggregate = new SampleAggregate();

        aggregate.clearWorkUnitState();

        assertThat(aggregate.isEntityDelete()).isFalse();
        assertThat(aggregate.isNew()).isFalse();
    }
}
