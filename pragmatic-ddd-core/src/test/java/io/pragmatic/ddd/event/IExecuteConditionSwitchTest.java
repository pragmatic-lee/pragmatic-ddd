package io.pragmatic.ddd.event;

import io.pragmatic.ddd.event.local.ThreadPoolEventManager;
import io.pragmatic.ddd.event.spi.ExecuteStatus;
import io.pragmatic.ddd.event.spi.IExecuteCondition;
import io.pragmatic.ddd.event.internal.defaults.DefaultExecuteCondition;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@link IExecuteCondition#switchStatus} 订阅者级开关的默认行为与事件管理器双重判断测试。
 *
 * @author wizard-lee
 */
public class IExecuteConditionSwitchTest {

    /** 按别名返回固定开关状态的条件；事件级条件保持默认执行。 */
    static class AliasSwitchCondition<T extends IDomainEvent> extends DefaultExecuteCondition<T> {
        private final String offAlias;

        AliasSwitchCondition(String offAlias) {
            this.offAlias = offAlias;
        }

        @Override
        public ExecuteStatus switchStatus(String alias) {
            return offAlias.equals(alias) ? ExecuteStatus.SKIP : ExecuteStatus.EXECUTE;
        }
    }

    @Test
    public void switchStatus_default_isExecute() {
        IExecuteCondition<TestDomainEvent> condition = new DefaultExecuteCondition<>();
        assertTrue(condition.switchStatus("ANY_ALIAS") == ExecuteStatus.EXECUTE);
        assertTrue(condition.switchStatus(null) == ExecuteStatus.EXECUTE);
    }

    @Test
    public void switchStatus_overridden_returnsSkip() {
        IExecuteCondition<TestDomainEvent> condition = new AliasSwitchCondition<>("OFF_ALIAS");
        assertTrue(condition.switchStatus("OFF_ALIAS") == ExecuteStatus.SKIP);
        assertTrue(condition.switchStatus("ON_ALIAS") == ExecuteStatus.EXECUTE);
    }

    @Test
    public void subscriberSwitch_off_skipsLocalSubscription() throws InterruptedException {
        ThreadPoolEventManager manager = new ThreadPoolEventManager();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger count = new AtomicInteger(0);

        IExecuteCondition<TestDomainEvent> condition = new AliasSwitchCondition<>("OFF_SUB");
        manager.registerSubscriber("ON_SUB", TestDomainEvent.class, s -> {
            count.incrementAndGet();
            latch.countDown();
        }, condition);
        manager.registerSubscriber("OFF_SUB", TestDomainEvent.class, s -> {
            count.incrementAndGet();
            latch.countDown();
        }, condition);

        // 发布时 OFF_SUB 被订阅者级开关跳过，仅 ON_SUB 执行
        manager.publish(new TestDomainEvent("111"));

        // 等待异步执行
        Thread.sleep(200);
        assertEquals(1, count.get());
    }

    @Test
    public void subscriberSwitch_on_runsLocalSubscription() throws InterruptedException {
        ThreadPoolEventManager manager = new ThreadPoolEventManager();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger count = new AtomicInteger(0);

        IExecuteCondition<TestDomainEvent> condition = new AliasSwitchCondition<>("OFF_SUB");
        manager.registerSubscriber("ON_SUB", TestDomainEvent.class, s -> {
            count.incrementAndGet();
            latch.countDown();
        }, condition);

        manager.publish(new TestDomainEvent("111"));
        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertEquals(1, count.get());
    }
}
