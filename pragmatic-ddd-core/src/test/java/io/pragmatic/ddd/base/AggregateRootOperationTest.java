package io.pragmatic.ddd.base;

import io.pragmatic.ddd.base.fixture.SampleAggregate;
import io.pragmatic.ddd.base.fixture.SampleMessages;
import io.pragmatic.ddd.operation.EntityOperation;
import io.pragmatic.ddd.operation.OperationException;
import io.pragmatic.ddd.operation.SampleRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 对应设计文档阶段 6.10：AggregateRoot 操作追踪测试（吸收原 T7）。
  * @author wizard-lee
 */
class AggregateRootOperationTest {

    @Test
    void recordOperation_thenHasOperation() {
        SampleAggregate entity = new SampleAggregate(SampleMessages.INSTANCE, new SampleRegistry());
        entity.recordOperation(SampleRegistry.A);
        assertThat(entity.hasOperation(SampleRegistry.A)).isTrue();
        assertThat(entity.hasOperation(SampleRegistry.B)).isFalse();
    }

    @Test
    void hasAllOperations_hasAnyOperation_combinations() {
        SampleAggregate entity = new SampleAggregate(SampleMessages.INSTANCE, new SampleRegistry());
        entity.recordOperation(SampleRegistry.A);
        entity.recordOperation(SampleRegistry.B);
        assertThat(entity.hasAllOperations(SampleRegistry.A, SampleRegistry.B)).isTrue();
        assertThat(entity.hasAllOperations(SampleRegistry.A, SampleRegistry.C)).isFalse();
        assertThat(entity.hasAnyOperation(SampleRegistry.A, SampleRegistry.C)).isTrue();
        assertThat(entity.hasAnyOperation(SampleRegistry.C)).isFalse();
    }

    @Test
    void noOperationRegistry_recordOperation_throws() {
        SampleAggregate entity = new SampleAggregate(SampleMessages.INSTANCE, null);
        assertThatThrownBy(() -> entity.recordOperation(SampleRegistry.A))
                .isInstanceOf(OperationException.class);
    }

    @Test
    void noOperationRegistry_hasOperation_throws() {
        SampleAggregate entity = new SampleAggregate(SampleMessages.INSTANCE, null);
        assertThatThrownBy(() -> entity.hasOperation(SampleRegistry.A))
                .isInstanceOf(OperationException.class);
    }
}
