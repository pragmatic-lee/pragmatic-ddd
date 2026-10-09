package io.pragmatic.ddd.base;

import io.pragmatic.ddd.base.fixture.SampleAggregate;
import io.pragmatic.ddd.base.fixture.SampleMessages;
import io.pragmatic.ddd.operation.MultipleOperationsException;
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
    void recordOperation_secondDifferentCode_throwsMultipleOperationsException() {
        SampleAggregate entity = new SampleAggregate(SampleMessages.INSTANCE, new SampleRegistry());
        entity.recordOperation(SampleRegistry.A);
        assertThatThrownBy(() -> entity.recordOperation(SampleRegistry.B))
                .isInstanceOf(MultipleOperationsException.class);
    }

    @Test
    void hasAnyOperation_matchesCurrentOperation() {
        SampleAggregate entity = new SampleAggregate(SampleMessages.INSTANCE, new SampleRegistry());
        entity.recordOperation(SampleRegistry.A);
        assertThat(entity.hasAnyOperation(SampleRegistry.A, SampleRegistry.C)).isTrue();
        assertThat(entity.hasAnyOperation(SampleRegistry.B, SampleRegistry.C)).isFalse();
    }

    @Test
    void hasExceptOperation_matchesOtherOperations() {
        SampleAggregate entity = new SampleAggregate(SampleMessages.INSTANCE, new SampleRegistry());
        entity.recordOperation(SampleRegistry.A);
        assertThat(entity.hasExceptOperation(SampleRegistry.B, SampleRegistry.C)).isTrue();
        assertThat(entity.hasExceptOperation(SampleRegistry.A, SampleRegistry.B)).isFalse();
    }

    @Test
    void hasExceptOperation_noOperation_returnsFalse() {
        SampleAggregate entity = new SampleAggregate(SampleMessages.INSTANCE, new SampleRegistry());
        assertThat(entity.hasExceptOperation(SampleRegistry.B, SampleRegistry.C)).isFalse();
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
