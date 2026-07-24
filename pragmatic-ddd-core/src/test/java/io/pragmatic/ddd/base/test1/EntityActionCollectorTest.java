package io.pragmatic.ddd.base.test1;

import io.pragmatic.ddd.operation.EntityOperation;
import io.pragmatic.ddd.operation.OperationRegistry;
import io.pragmatic.ddd.operation.TriggeredOperations;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;


public class EntityActionCollectorTest {


    private TriggeredOperations entityActionCollector;

    @Before
    public void init() {
        entityActionCollector = new TriggeredOperations(new TestAction());
    }


    @Test
    public void testContainActions() {
        entityActionCollector.clear();
        entityActionCollector.put(TestAction.actionA);
        boolean result = entityActionCollector.containActions(TestAction.actionA);
        Assert.assertTrue(result);
    }

    @Test
    public void testContainAnyAction() throws Exception {

        entityActionCollector.clear();
        entityActionCollector.put(TestAction.actionA);
        entityActionCollector.put(TestAction.actionB);

        boolean result = entityActionCollector.containAnyAction(TestAction.actionA);
        Assert.assertTrue(result);
        boolean resultFalse = entityActionCollector.containAnyAction(TestAction.actionC);
        Assert.assertFalse(resultFalse);
    }

    @Test
    public void testContainAction() {

        entityActionCollector.clear();
        entityActionCollector.put(TestAction.actionA);

        boolean result = entityActionCollector.containAction(TestAction.actionA);
        Assert.assertTrue(result);
    }

    @Test
    public void testNotContainAction() {
        entityActionCollector.clear();
        entityActionCollector.put(TestAction.actionA);
        boolean result = entityActionCollector.notContainAction(TestAction.actionB);
        Assert.assertTrue(result);
    }
}

class TestAction extends OperationRegistry {

    public static final EntityOperation actionA = EntityOperation.of("actionA");
    public static final EntityOperation actionB = EntityOperation.of("actionB");
    public static final EntityOperation actionC = EntityOperation.of("actionC");

}
