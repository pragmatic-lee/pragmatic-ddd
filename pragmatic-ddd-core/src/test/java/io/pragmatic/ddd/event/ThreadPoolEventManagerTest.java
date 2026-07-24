package io.pragmatic.ddd.event;

import io.pragmatic.ddd.event.internal.defaults.BaseEventHandler;
import io.pragmatic.ddd.event.internal.defaults.SubscriberOrderManager;
import io.pragmatic.ddd.event.internal.model.DeliveryPolicy;
import io.pragmatic.ddd.event.local.ThreadPoolEventManager;
import io.pragmatic.ddd.event.spi.*;
import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static io.pragmatic.ddd.event.internal.model.DeliveryPolicy.DELAYED;

/**
 * 基于线程池的领域事件发布订阅管理器
 *
 * @author lixiaojing
 * @date 2020/9/13 12:41 下午
 */
public class ThreadPoolEventManagerTest {

    /**
     * 验证发布事件，执行指定订阅，并执行指定订阅的依赖的订阅
     * 执行 sub2 -> sub1
     *
     * @throws InterruptedException
     */
    @Test
    public void publishEventOneTest() throws InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(2);
        AtomicInteger atomicInteger = new AtomicInteger(0);


        ThreadPoolEventManager manager = new ThreadPoolEventManager();

        manager.registerSubscriber("sub1", TestDomainEvent.class, s -> {

            countDownLatch.countDown();
            atomicInteger.incrementAndGet();
            System.out.println("sub1");

        }, null, "sub2");

        manager.registerSubscriber("sub2", TestDomainEvent.class, s -> {

            countDownLatch.countDown();
            atomicInteger.incrementAndGet();
            System.out.println("sub2");
        }, null, "sub3");

        manager.registerSubscriber("sub3", TestDomainEvent.class, s -> {

            countDownLatch.countDown();
            atomicInteger.incrementAndGet();
            System.out.println("sub3");
        });
        //发布事件，执行sub2的订阅，并执行依赖sub2的订阅sub1
        manager.publish(new TestDomainEvent("111"), "sub2");

        countDownLatch.await();

        Assert.assertEquals(2, atomicInteger.get());
    }

    /**
     * 验证执行指定的订阅，但只是指行当前指定的，不执行依赖的
     * 执行 sub2
     *
     * @throws InterruptedException
     */
    @Test
    public void publishEventOneOnlyTest() throws InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        AtomicInteger atomicInteger = new AtomicInteger(0);


        ThreadPoolEventManager manager = new ThreadPoolEventManager();

        manager.registerSubscriber("sub1", TestDomainEvent.class, s -> {

            countDownLatch.countDown();
            atomicInteger.incrementAndGet();
            System.out.println("sub1");

        }, null, "sub2");

        manager.registerSubscriber("sub2", TestDomainEvent.class, s -> {

            countDownLatch.countDown();
            atomicInteger.incrementAndGet();
            System.out.println("sub2");
        }, null, "sub3");

        manager.registerSubscriber("sub3", TestDomainEvent.class, s -> {

            countDownLatch.countDown();
            atomicInteger.incrementAndGet();
            System.out.println("sub3");
        });
        //发布事件，但只执行sub2的订阅，不执行依赖sub2的sub1的订阅
        manager.publish(new TestDomainEvent(""), "sub2", true);

        countDownLatch.await();

        Assert.assertEquals(1, atomicInteger.get());
    }

    /**
     * 验证订阅按依赖顺序执行 sub3->sub2->sub1 输出 3 2 1
     *
     * @throws InterruptedException
     */
    @Test
    public void oneEventTwoSubscriberOrderedTest() throws InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(3);
        AtomicInteger atomicInteger = new AtomicInteger(0);


        ThreadPoolEventManager manager = new ThreadPoolEventManager();

        manager.registerSubscriber("sub1", TestDomainEvent.class, s -> {

            countDownLatch.countDown();
            atomicInteger.incrementAndGet();
            System.out.println(1);

        }, null, "sub2");

        manager.registerSubscriber("sub2", TestDomainEvent.class, s -> {

            countDownLatch.countDown();
            atomicInteger.incrementAndGet();
            System.out.println(2);
        }, null, "sub3");

        manager.registerSubscriber("sub3", TestDomainEvent.class, s -> {

            countDownLatch.countDown();
            atomicInteger.incrementAndGet();
            System.out.println(3);
        });

        manager.publish(new TestDomainEvent(""));

        countDownLatch.await();

        Assert.assertEquals(3, atomicInteger.get());
    }

    /**
     * 验证订阅没有依赖无特定顺序执行 输出  1 2 3
     *
     * @throws InterruptedException
     */
    @Test
    public void oneEventTwoSubscriberTest() throws InterruptedException {

        CountDownLatch countDownLatch = new CountDownLatch(3);
        AtomicInteger atomicInteger = new AtomicInteger(0);


        ThreadPoolEventManager manager = new ThreadPoolEventManager();

        manager.registerSubscriber("sub1", TestDomainEvent.class, s -> {

            countDownLatch.countDown();
            atomicInteger.incrementAndGet();
            System.out.println(1);
            System.out.println(Thread.currentThread().getName());

        });

        manager.registerSubscriber("sub2", TestDomainEvent.class, s -> {

            countDownLatch.countDown();
            atomicInteger.incrementAndGet();
            System.out.println(2);
            System.out.println(Thread.currentThread().getName());


        });

        manager.registerSubscriber("sub3", TestDomainEvent.class, s -> {

            countDownLatch.countDown();
            atomicInteger.incrementAndGet();
            System.out.println(3);
            System.out.println(Thread.currentThread().getName());


        });

        manager.publish(new TestDomainEvent(""));

        countDownLatch.await();

        Assert.assertEquals(3, atomicInteger.get());
    }

    /**
     * 验证订阅按满足特定条件执行，输出 2
     *
     * @throws InterruptedException
     */
    @Test
    public void oneEventTwoSubscriberWithConditionTest() throws InterruptedException {

        CountDownLatch countDownLatch = new CountDownLatch(1);
        AtomicInteger atomicInteger = new AtomicInteger(0);

        ThreadPoolEventManager manager = new ThreadPoolEventManager();
        manager.registerSubscriber("sub1", TestDomainEvent.class, s -> {

            countDownLatch.countDown();
            atomicInteger.incrementAndGet();
            System.out.println(1);

            //需要满足IExecuteCondition 条件才能执行
        }, (IExecuteCondition<TestDomainEvent>) evt -> evt.getEntityId().equals("1") ? ExecuteStatus.EXECUTE : ExecuteStatus.SKIP);

        manager.registerSubscriber("sub2", TestDomainEvent.class, s -> {
            countDownLatch.countDown();
            atomicInteger.incrementAndGet();
            System.out.println(2);


        });

        TestDomainEvent testDomainEvent = new TestDomainEvent("2");
        manager.publish(testDomainEvent);

        countDownLatch.await();

        Assert.assertEquals(1, atomicInteger.get());

    }

    @Test
    public void orderExecuteWithConditionTest() throws InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(2);

        ThreadPoolEventManager manager = new ThreadPoolEventManager(2, 4, 100, 3, 200, 1000, new SubscriberOrderManager());


        manager.registerSubscriber("sub1", TestDomainEvent.class, s -> {
                    countDownLatch.countDown();
                    System.out.println("sub1");
                }, null, "sub2"
        );

        manager.registerSubscriber("sub2", TestDomainEvent.class, s -> {
                    countDownLatch.countDown();
                    System.out.println("sub2");
                }, (IExecuteCondition<TestDomainEvent>) evt -> evt.getEntityId().equals("100") ? ExecuteStatus.EXECUTE : ExecuteStatus.SKIP
        );
        //发布事件 name=100 执行 sub2 ,sub1
        manager.publish(new TestDomainEvent("100"));
        //发布事件 name=200 不执行 sub2 和 sub1
        manager.publish(new TestDomainEvent("200"));

        Thread.sleep(5000);

        Assert.assertEquals(0L, countDownLatch.getCount());

    }

    /**
     * 验证延时执行订阅
     */
    @Test
    public void delaySubscriber() throws InterruptedException {

        CountDownLatch countDownLatch = new CountDownLatch(4);
        AtomicInteger atomicInteger = new AtomicInteger(0);


        ThreadPoolEventManager manager = new ThreadPoolEventManager(
                2, 4, 100, 3, 200, 1000, new SubscriberOrderManager());


        manager.registerSubscriber("sub0", TestDomainEvent.class, s -> {
            countDownLatch.countDown();
            atomicInteger.incrementAndGet();
            System.out.println(0);


        });

        manager.registerSubscriber("sub1", TestDomainEvent.class, s -> {
            countDownLatch.countDown();
            atomicInteger.incrementAndGet();
            System.out.println(1);


        }, DELAYED);

        manager.registerSubscriber("sub2", TestDomainEvent.class, s -> {
            countDownLatch.countDown();
            atomicInteger.incrementAndGet();
            System.out.println(2);


        }, DELAYED);

        manager.registerSubscriber("sub3", TestDomainEvent.class, s -> {
            countDownLatch.countDown();
            atomicInteger.incrementAndGet();
            System.out.println(3);


        }, DELAYED);


        manager.publish(new TestDomainEvent(""));

        countDownLatch.await();

        Assert.assertEquals(4, atomicInteger.get());

    }

    /**
     * 验证订阅执行重试 输出 1 1 1 1
     *
     * @throws InterruptedException
     */
    @Test
    public void oneEventOneSubscriberUseRetry() throws InterruptedException {

        CountDownLatch countDownLatch = new CountDownLatch(4);
        AtomicInteger atomicInteger = new AtomicInteger(0);


        //最大重试次数，不算首次调用
        ThreadPoolEventManager manager = new ThreadPoolEventManager(2, 4, 100, 3, 200, 1000, new SubscriberOrderManager());

        manager.registerSubscriber("sub2", TestDomainEvent.class, s -> {
            countDownLatch.countDown();
            atomicInteger.incrementAndGet();
            System.out.println(1);
            //模拟异常触发重试
            throw new RuntimeException();

        });

        manager.publish(new TestDomainEvent(""));

        countDownLatch.await();

        Assert.assertEquals(4, atomicInteger.get());

    }

    @Test
    public void useSubscriberRegisterCls() throws InterruptedException {
        ThreadPoolEventManager manager = new ThreadPoolEventManager(2,
                4, 100, 3, 200, 1000, new SubscriberOrderManager());

        CountDownLatch countDownLatch = new CountDownLatch(1);
        SubBaa subBaa = new SubBaa(TestDomainEvent.class, countDownLatch);

        subBaa.register(manager, "sub1");

        manager.publish(new TestDomainEvent("name123"));

        countDownLatch.await();
    }
}

class SubBaa extends BaseEventHandler<TestDomainEvent> {
    private final CountDownLatch countDownLatch;

    public SubBaa(Class<TestDomainEvent> cls, CountDownLatch countDownLatch) {
        super(cls);
        this.countDownLatch = countDownLatch;
    }

    @Override
    protected void handle(TestDomainEvent event) {
        System.out.println(event.getName());
        countDownLatch.countDown();
    }

    @Override
    public ExecuteStatus runCondition(TestDomainEvent event) {
        return event.getName().equals("name123") ? ExecuteStatus.EXECUTE : ExecuteStatus.SKIP;
    }
}
