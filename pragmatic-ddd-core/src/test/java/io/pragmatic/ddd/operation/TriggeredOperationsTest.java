package io.pragmatic.ddd.operation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TriggeredOperationsTest {

    private TriggeredOperations triggeredOperations;

    @BeforeEach
    void init() {
        triggeredOperations = new TriggeredOperations(new TestOperations());
    }

    @Test
    void testContainsAll() {
        triggeredOperations.clear();
        triggeredOperations.put(TestOperations.actionA);
        boolean result = triggeredOperations.containsAll(TestOperations.actionA);
        assertThat(result).isTrue();
    }

    @Test
    void testContainsAny() {
        triggeredOperations.clear();
        triggeredOperations.put(TestOperations.actionA);
        triggeredOperations.put(TestOperations.actionB);

        boolean result = triggeredOperations.containsAny(TestOperations.actionA);
        assertThat(result).isTrue();
        boolean resultFalse = triggeredOperations.containsAny(TestOperations.actionC);
        assertThat(resultFalse).isFalse();
    }

    @Test
    void testContains() {
        triggeredOperations.clear();
        triggeredOperations.put(TestOperations.actionA);

        boolean result = triggeredOperations.contains(TestOperations.actionA);
        assertThat(result).isTrue();
    }

    @Test
    void testNotContains() {
        triggeredOperations.clear();
        triggeredOperations.put(TestOperations.actionA);
        boolean result = !triggeredOperations.contains(TestOperations.actionB);
        assertThat(result).isTrue();
    }
}

class TestOperations extends OperationRegistry {

    public static final EntityOperation actionA = EntityOperation.of("actionA");
    public static final EntityOperation actionB = EntityOperation.of("actionB");
    public static final EntityOperation actionC = EntityOperation.of("actionC");

}
