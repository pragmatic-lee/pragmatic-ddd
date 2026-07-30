package io.pragmatic.ddd.event.local;

import io.pragmatic.ddd.event.IDomainEvent;
import io.pragmatic.ddd.event.spi.IEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 事件投递任务：封装订阅者与事件，执行时调用订阅者处理事件，
 * 失败时按配置的最大重试次数进行延时重试。
 *
 * @author wizard-lee
 */
class Task<T extends IDomainEvent> implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(Task.class);

    private final IEventListener<T> subscriber;
    private final T domainEvent;
    private final AtomicInteger retryTimes = new AtomicInteger(0);
    private final ScheduledExecutorService delayScheduler;
    private final ExecutorService taskExecutor;
    private final int maxRetryTimes;
    private final int retryDelayTime;
    private final ITaskCallback callback;

    /** 创建投递任务，绑定订阅者、事件、重试参数与两个线程池。 */
    public Task(IEventListener<T> subscriber, T domainEvent, int maxRetryTimes, int retryDelayTime,
                ScheduledExecutorService delayScheduler, ExecutorService taskExecutor,
                ITaskCallback callback) {
        this.subscriber = subscriber;
        this.domainEvent = domainEvent;
        this.maxRetryTimes = maxRetryTimes;
        this.retryDelayTime = retryDelayTime;
        this.delayScheduler = delayScheduler;
        this.taskExecutor = taskExecutor;
        this.callback = callback;
    }

    @Override
    public void run() {
        try {
            this.subscriber.handleEvent(this.domainEvent);
            this.callback.execute(this);
        } catch (Exception ex) {
            int times = this.retryTimes.get();
            if (times < this.maxRetryTimes) {
                this.retryTimes.incrementAndGet();
                // 重试：调度器延时 → 执行器执行
                this.delayScheduler.schedule(
                        () -> this.taskExecutor.execute(this),
                        this.retryDelayTime, TimeUnit.MILLISECONDS);
            } else {
                log.error("Task retry exhausted after {} attempts, event={}, subscriber={}",
                        this.maxRetryTimes + 1,
                        this.domainEvent.getClass().getSimpleName(),
                        this.subscriber.subscribedToEventType().getSimpleName(), ex);
            }
        }
    }
}
