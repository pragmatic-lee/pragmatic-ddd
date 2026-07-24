package io.pragmatic.ddd.base.test1;

import io.pragmatic.ddd.operation.EntityOperation;
import io.pragmatic.ddd.operation.OperationRegistry;
import io.pragmatic.ddd.operation.TriggeredOperations;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;


public class TriggeredOperationsTest {


    private TriggeredOperations triggeredOperations;

    @Before
    public void init() {
        triggeredOperations = new TriggeredOperations(new TestOperations());
    }


    @Test
    public void testContainsAll() {
        triggeredOperations.clear();
        triggeredOperations.put(TestOperations.actionA);
        boolean result = triggeredOperations.containsAll(TestOperations.actionA);
        Assert.assertTrue(result);
    }

    @Test
    public void testContainsAny() throws Exception {

        triggeredOperations.clear();
        triggeredOperations.put(TestOperations.actionA);
        triggeredOperations.put(TestOperations.actionB);

        boolean result = triggeredOperations.containsAny(TestOperations.actionA);
        Assert.assertTrue(result);
        boolean resultFalse = triggeredOperations.containsAny(TestOperations.actionC);
        Assert.assertFalse(resultFalse);
    }

    @Test
    public void testContains() {

        triggeredOperations.clear();
        triggeredOperations.put(TestOperations.actionA);

        boolean result = triggeredOperations.contains(TestOperations.actionA);
        Assert.assertTrue(result);
    }

    @Test
    public void testNotContains() {
        triggeredOperations.clear();
        triggeredOperations.put(TestOperations.actionA);
        boolean result = !triggeredOperations.contains(TestOperations.actionB);
        Assert.assertTrue(result);
    }
}

class TestOperations extends OperationRegistry {

    public static final EntityOperation actionA = EntityOperation.of("actionA");
    public static final EntityOperation actionB = EntityOperation.of("actionB");
    public static final EntityOperation actionC = EntityOperation.of("actionC");

}
