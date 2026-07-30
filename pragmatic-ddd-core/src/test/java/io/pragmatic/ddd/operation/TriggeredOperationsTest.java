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
    void put_registeredOperation_thenContains() {
        triggeredOperations.put(SampleRegistry.A);

        assertThat(triggeredOperations.contains(SampleRegistry.A)).isTrue();
        assertThat(triggeredOperations.contains(SampleRegistry.B)).isFalse();
    }

    @Test
    void put_unregisteredOperation_throwsOperationException() {
        EntityOperation unregistered = EntityOperation.of("UNREGISTERED");

        assertThatThrownBy(() -> triggeredOperations.put(unregistered))
                .isInstanceOf(OperationException.class)
                .hasMessageContaining("UNREGISTERED");
    }

    @Test
    void put_builtinNewAndDelete_thenContains() {
        triggeredOperations.put(OperationRegistry.NEW);
        triggeredOperations.put(OperationRegistry.DELETE);

        assertThat(triggeredOperations.containsAll(OperationRegistry.NEW, OperationRegistry.DELETE)).isTrue();
    }

    @Test
    void containsAll_allTriggered_returnsTrue() {
        triggeredOperations.put(SampleRegistry.A);
        triggeredOperations.put(SampleRegistry.B);

        assertThat(triggeredOperations.containsAll(SampleRegistry.A, SampleRegistry.B)).isTrue();
    }

    @Test
    void containsAll_partialTriggered_returnsFalse() {
        triggeredOperations.put(SampleRegistry.A);

        assertThat(triggeredOperations.containsAll(SampleRegistry.A, SampleRegistry.B)).isFalse();
    }

    @Test
    void containsAny_anyTriggered_returnsTrue() {
        triggeredOperations.put(SampleRegistry.A);

        assertThat(triggeredOperations.containsAny(SampleRegistry.A, SampleRegistry.C)).isTrue();
    }

    @Test
    void containsAny_noneTriggered_returnsFalse() {
        triggeredOperations.put(SampleRegistry.A);

        assertThat(triggeredOperations.containsAny(SampleRegistry.B, SampleRegistry.C)).isFalse();
    }

    @Test
    void clear_thenNothingContained() {
        triggeredOperations.put(SampleRegistry.A);
        triggeredOperations.put(SampleRegistry.B);

        triggeredOperations.clear();

        assertThat(triggeredOperations.contains(SampleRegistry.A)).isFalse();
        assertThat(triggeredOperations.contains(SampleRegistry.B)).isFalse();
    }
}
