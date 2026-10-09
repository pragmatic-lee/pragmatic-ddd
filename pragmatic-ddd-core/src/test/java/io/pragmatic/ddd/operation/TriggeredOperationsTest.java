package io.pragmatic.ddd.operation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TriggeredOperations 已触发操作收集器测试。
 *
 * @author wizard-lee
 */
class TriggeredOperationsTest {

    private TriggeredOperations triggeredOperations;

    @BeforeEach
    void init() {
        triggeredOperations = new TriggeredOperations(new SampleRegistry());
    }

    @Test
    void record_registeredOperation_thenContains() {
        triggeredOperations.record(SampleRegistry.A);

        assertThat(triggeredOperations.contains(SampleRegistry.A)).isTrue();
        assertThat(triggeredOperations.contains(SampleRegistry.B)).isFalse();
    }

    @Test
    void record_unregisteredOperation_throwsOperationException() {
        EntityOperation unregistered = EntityOperation.of("UNREGISTERED");

        assertThatThrownBy(() -> triggeredOperations.record(unregistered))
                .isInstanceOf(OperationException.class)
                .hasMessageContaining("UNREGISTERED");
    }

    @Test
    void record_sameCodeTwice_idempotent() {
        triggeredOperations.record(SampleRegistry.A);
        triggeredOperations.record(SampleRegistry.A);

        assertThat(triggeredOperations.contains(SampleRegistry.A)).isTrue();
        assertThat(triggeredOperations.current()).contains(SampleRegistry.A);
    }

    @Test
    void record_secondDifferentCode_throwsMultipleOperationsException() {
        triggeredOperations.record(SampleRegistry.A);

        assertThatThrownBy(() -> triggeredOperations.record(SampleRegistry.B))
                .isInstanceOf(MultipleOperationsException.class)
                .hasMessageContaining("A")
                .hasMessageContaining("B");
    }

    @Test
    void containsAny_currentInSet_returnsTrue() {
        triggeredOperations.record(SampleRegistry.A);

        assertThat(triggeredOperations.containsAny(SampleRegistry.A, SampleRegistry.C)).isTrue();
    }

    @Test
    void containsAny_currentNotInSet_returnsFalse() {
        triggeredOperations.record(SampleRegistry.A);

        assertThat(triggeredOperations.containsAny(SampleRegistry.B, SampleRegistry.C)).isFalse();
    }

    @Test
    void containsAny_notRecorded_returnsFalse() {
        assertThat(triggeredOperations.containsAny(SampleRegistry.A, SampleRegistry.B)).isFalse();
    }

    @Test
    void containsExceptOperation_currentNotInSet_returnsTrue() {
        triggeredOperations.record(SampleRegistry.A);

        assertThat(triggeredOperations.containsExceptOperation(SampleRegistry.B, SampleRegistry.C)).isTrue();
    }

    @Test
    void containsExceptOperation_currentInSet_returnsFalse() {
        triggeredOperations.record(SampleRegistry.A);

        assertThat(triggeredOperations.containsExceptOperation(SampleRegistry.A, SampleRegistry.C)).isFalse();
    }

    @Test
    void containsExceptOperation_notRecorded_returnsFalse() {
        assertThat(triggeredOperations.containsExceptOperation(SampleRegistry.A, SampleRegistry.B)).isFalse();
    }

    @Test
    void current_notRecorded_empty() {
        assertThat(triggeredOperations.current()).isEmpty();
    }

    @Test
    void current_afterRecord_returnsOperation() {
        triggeredOperations.record(SampleRegistry.A);

        assertThat(triggeredOperations.current()).contains(SampleRegistry.A);
    }

    @Test
    void clear_thenNothingContained() {
        triggeredOperations.record(SampleRegistry.A);

        triggeredOperations.clear();

        assertThat(triggeredOperations.contains(SampleRegistry.A)).isFalse();
        assertThat(triggeredOperations.current()).isEmpty();
    }
}
