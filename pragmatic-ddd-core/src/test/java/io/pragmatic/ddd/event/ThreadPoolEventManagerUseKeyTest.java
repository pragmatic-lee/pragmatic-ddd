package io.pragmatic.ddd.event;

import io.pragmatic.ddd.event.internal.defaults.SubscriberOrderManager;
import io.pragmatic.ddd.event.internal.defaults.SubscriberFactory;
import io.pragmatic.ddd.event.local.ThreadPoolEventManager;
import io.pragmatic.ddd.event.spi.ExecuteStatus;
import io.pragmatic.ddd.event.spi.IExecuteCondition;
import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 基于线程池的领域事件发布订阅管理器
 *
 * @author lixiaojing
 * @date 2020/9/13 12:41 下午
 */
public class ThreadPoolEventManagerUseKeyTest {

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

        manager.registerSubscriber(SubscriberKey.SUB1, TestDomainEvent.class, s -> {

            countDownLatch.countDown();
            atomicInteger.incrementAndGet();
            System.out.println(1);

        }, null, SubscriberKey.SUB2);

        manager.registerSubscriber(SubscriberKey.SUB2, TestDomainEvent.class, s -> {

            countDownLatch.countDown();
            atomicInteger.incrementAndGet();
            System.out.println(2);
        }, null, SubscriberKey.SUB3);

        manager.registerSubscriber(SubscriberKey.SUB3, TestDomainEvent.class, s -> {

            countDownLatch.countDown();
            atomicInteger.incrementAndGet();
            System.out.println(3);
        });
        //发布事件，执行sub2的订阅，并执行依赖sub2的订阅sub1
        manager.publish(new TestDomainEvent(""), "sub2");

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

        manager.registerSubscriber(SubscriberKey.SUB1, TestDomainEvent.class, s -> {

            countDownLatch.countDown();
            atomicInteger.incrementAndGet();
            System.out.println(1);

        }, null, SubscriberKey.SUB2);

        manager.registerSubscriber(SubscriberKey.SUB2, TestDomainEvent.class, s -> {

            countDownLatch.countDown();
            atomicInteger.incrementAndGet();
            System.out.println(2);
        }, null, SubscriberKey.SUB3);

        manager.registerSubscriber(SubscriberKey.SUB3, TestDomainEvent.class, s -> {

            countDownLatch.countDown();
            atomicInteger.incrementAndGet();
            System.out.println(3);
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

        manager.registerSubscriber(SubscriberKey.SUB1, TestDomainEvent.class, s -> {

            countDownLatch.countDown();
            atomicInteger.incrementAndGet();
            System.out.println(1);

        }, null, SubscriberKey.SUB2);

        manager.registerSubscriber(SubscriberKey.SUB2, TestDomainEvent.class, s -> {

            countDownLatch.countDown();
            atomicInteger.incrementAndGet();
            System.out.println(2);
        }, null, SubscriberKey.SUB3);

        manager.registerSubscriber(SubscriberKey.SUB3, TestDomainEvent.class, s -> {

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

        manager.registerSubscriber(SubscriberKey.SUB1, TestDomainEvent.class, s -> {

            countDownLatch.countDown();
            atomicInteger.incrementAndGet();
            System.out.println(1);
            System.out.println(Thread.currentThread().getName());

        });

        manager.registerSubscriber(SubscriberKey.SUB2, TestDomainEvent.class, s -> {

            countDownLatch.countDown();
            atomicInteger.incrementAndGet();
            System.out.println(2);
            System.out.println(Thread.currentThread().getName());


        });

        manager.registerSubscriber(SubscriberKey.SUB3, TestDomainEvent.class, s -> {

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
        manager.registerSubscriber(SubscriberKey.SUB1, TestDomainEvent.class, s -> {

            countDownLatch.countDown();
            atomicInteger.incrementAndGet();
            System.out.println(1);

            //需要满足IExecuteCondition 条件才能执行
        }, evt -> evt.getEntityId().equals("1") ? ExecuteStatus.EXECUTE : ExecuteStatus.SKIP);

        manager.registerSubscriber(SubscriberKey.SUB2, TestDomainEvent.class, s -> {
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


        manager.registerSubscriber(SubscriberKey.SUB1, TestDomainEvent.class, s -> {
                    countDownLatch.countDown();
                    System.out.println("sub1");
                }, null, SubscriberKey.SUB2
        );

        manager.registerSubscriber(SubscriberKey.SUB2, TestDomainEvent.class, s -> {
                    countDownLatch.countDown();
                    System.out.println("sub2");
                }, evt -> evt.getEntityId().equals("100") ? ExecuteStatus.EXECUTE : ExecuteStatus.SKIP
        );
        //发布事件 name=100 执行 sub2 ,sub1
        manager.publish(new TestDomainEvent("100"));
        //发布事件 name=200 不执行 sub2 和 sub1
        manager.publish(new TestDomainEvent("200"));

        Thread.sleep(5000);

        Assert.assertEquals(0L, countDownLatch.getCount());

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

        manager.registerSubscriber(SubscriberKey.SUB2, TestDomainEvent.class, s -> {
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
}
