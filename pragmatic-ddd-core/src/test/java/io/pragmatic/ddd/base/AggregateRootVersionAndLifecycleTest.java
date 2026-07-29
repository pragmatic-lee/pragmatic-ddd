package io.pragmatic.ddd.base;

import io.pragmatic.ddd.base.fixture.SampleAggregate;
import io.pragmatic.ddd.base.fixture.SampleEvent;
import io.pragmatic.ddd.base.fixture.SampleMessages;
import io.pragmatic.ddd.operation.OperationException;
import io.pragmatic.ddd.operation.SampleRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 对应设计文档阶段 6.11：AggregateRoot 版本号、新建标记、工作单元清理测试（吸收原 T8）。
 */
class AggregateRootVersionAndLifecycleTest {

    @Test
    void getNewVersion_firstCall_oldPlusOne() {
        SampleAggregate entity = new SampleAggregate(SampleMessages.INSTANCE, new SampleRegistry());
        assertThat(entity.getOldVersion()).isEqualTo(1);
        assertThat(entity.getNewVersion()).isEqualTo(2);
    }

    @Test
    void getNewVersion_idempotent() {
        SampleAggregate entity = new SampleAggregate(SampleMessages.INSTANCE, new SampleRegistry());
        long v1 = entity.getNewVersion();
        long v2 = entity.getNewVersion();
        assertThat(v2).isEqualTo(v1);
        assertThat(v2).isEqualTo(2);
    }

    @Test
    void isNew_defaultFalse_markNew_true() {
        SampleAggregate entity = new SampleAggregate(SampleMessages.INSTANCE, new SampleRegistry());
        assertThat(entity.isNew()).isFalse();
        entity.markNew();
        assertThat(entity.isNew()).isTrue();
    }

    @Test
    void clearWorkUnitState_clearsEventsOperationsPointer() {
        SampleAggregate entity = new SampleAggregate(SampleMessages.INSTANCE, new SampleRegistry());
        entity.recordOperation(SampleRegistry.A);
        entity.collectEvent(new SampleEvent("1"));
        assertThat(entity.getDomainEvents()).isNotEmpty();
        assertThat(entity.hasOperation(SampleRegistry.A)).isTrue();

        entity.clearWorkUnitState();
        assertThat(entity.getDomainEvents()).isEmpty();
        assertThat(entity.hasOperation(SampleRegistry.A)).isFalse();
        // 指针已重置 → 再 collect 应 fail-fast
        assertThatThrownBy(() -> entity.collectEvent(new SampleEvent("1")))
                .isInstanceOf(OperationException.class);
    }

    @Test
    void clearWorkUnitState_neverRecorded_noException() {
        SampleAggregate entity = new SampleAggregate(SampleMessages.INSTANCE, null);
        assertThatCode(entity::clearWorkUnitState).doesNotThrowAnyException();
    }
}
