package io.pragmatic.ddd.event.local;

import io.pragmatic.ddd.event.IDomainEvent;
import io.pragmatic.ddd.event.internal.manager.AbstractEventManager;
import io.pragmatic.ddd.event.internal.defaults.SubscriberOrderManager;
import io.pragmatic.ddd.event.internal.model.SubscriberInfo;
import io.pragmatic.ddd.event.spi.ExecuteStatus;
import io.pragmatic.ddd.event.spi.IEventListener;
import io.pragmatic.ddd.event.spi.ISubscriberOrderManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 基于线程池的事件任务处理器。
 * <p>采用"调度器 + 执行器分离"架构：延时任务由调度器等待，到期后提交到执行器处理。</p>
 */
public class ThreadPoolEventManager extends AbstractEventManager {

    private static final Logger log = LoggerFactory.getLogger(ThreadPoolEventManager.class);

    /** 默认调度器线程数 */
    private static final int DEFAULT_SCHEDULER_THREADS = 2;

    /** 默认执行器核心线程数 */
    private static final int DEFAULT_CORE_POOL_SIZE =
            Math.max(4, Runtime.getRuntime().availableProcessors());

    /** 默认执行器最大线程数 */
    private static final int DEFAULT_MAX_POOL_SIZE =
            Math.max(8, Runtime.getRuntime().availableProcessors() * 2);

    /** 默认队列容量 */
    private static final int DEFAULT_QUEUE_CAPACITY = 1000;

    /** 默认线程空闲保活时间（秒） */
    private static final long DEFAULT_KEEP_ALIVE_SECONDS = 60;

    /** 默认投递延迟（毫秒） */
    private static final int DEFAULT_DELIVERY_DELAY_MS = 1000;

    /** 默认最大重试次数 */
    private static final int DEFAULT_MAX_RETRY_TIMES = 3;

    /** 默认重试延迟（毫秒） */
    private static final int DEFAULT_RETRY_DELAY_MS = 1500;

    private final ScheduledExecutorService delayScheduler;
    private final ThreadPoolExecutor taskExecutor;
    private final int maxRetryTimes;
    private final int retryDelayMs;
    private final int deliveryDelayMs;

    // ── 构造器 ──

    public ThreadPoolEventManager() {
        this(DEFAULT_CORE_POOL_SIZE, DEFAULT_MAX_POOL_SIZE, DEFAULT_QUEUE_CAPACITY,
                DEFAULT_MAX_RETRY_TIMES, DEFAULT_RETRY_DELAY_MS, DEFAULT_DELIVERY_DELAY_MS,
                new SubscriberOrderManager());
    }

    public ThreadPoolEventManager(
            int corePoolSize, int maxPoolSize, int queueCapacity,
            int maxRetryTimes, int retryDelayMs, int deliveryDelayMs,
            ISubscriberOrderManager orderManager) {
        super("", orderManager);
        this.maxRetryTimes = maxRetryTimes;
        this.retryDelayMs = retryDelayMs;
        this.deliveryDelayMs = deliveryDelayMs;

        // 调度器：2 线程，只负责等待延时，不执行业务逻辑
        this.delayScheduler = new ScheduledThreadPoolExecutor(
                DEFAULT_SCHEDULER_THREADS,
                createThreadFactory("domain-event-scheduler-"));

        // 执行器：有界队列 + CallerRunsPolicy，背压保护
        this.taskExecutor = new ThreadPoolExecutor(
                corePoolSize,
                maxPoolSize,
                DEFAULT_KEEP_ALIVE_SECONDS,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(queueCapacity),
                createThreadFactory("domain-event-executor-"));
        this.taskExecutor.setRejectedExecutionHandler(
                new ThreadPoolExecutor.CallerRunsPolicy());
    }

    // ── 线程工厂 ──

    private final AtomicInteger threadNumber = new AtomicInteger(1);

    private ThreadFactory createThreadFactory(String namePrefix) {
        return r -> {
            Thread thread = new Thread(r);
            thread.setName(namePrefix + threadNumber.getAndIncrement());
            thread.setDaemon(true);
            thread.setUncaughtExceptionHandler((t, e) ->
                    log.error("Uncaught exception in thread {}", t.getName(), e));
            return thread;
        };
    }


    // ── publish ──

    @SuppressWarnings("unchecked")
    @Override
    public <T extends IDomainEvent> void publish(T obj) {
        String eventName = this.resolveEventName(obj.getClass());
        Map<String, SubscriberInfo> subscriberMap = this.filterSubscriberInfoMap(eventName);

        for (Map.Entry<String, SubscriberInfo> entry : subscriberMap.entrySet()) {
            IEventListener<T> subscribedTo = (IEventListener<T>) entry.getValue().getSubscriber();
            if (subscribedTo != null
                    && this.executeCheck(obj, entry.getValue().getCondition()) == ExecuteStatus.EXECUTE) {
                long delayMs = entry.getValue().isDelayed() ? this.deliveryDelayMs : 0;
                this.submitTask(subscribedTo, obj, eventName, entry.getKey(), delayMs, false);
            }
        }
    }

    @Override
    public <T extends IDomainEvent> void publish(T obj, String subscriber) {
        this.publish(obj, subscriber, false);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends IDomainEvent> void publish(T obj, String subscriber, boolean onlyThis) {
        String eventName = this.resolveEventName(obj.getClass());
        SubscriberInfo info = this.findSubscriberInfo(obj, subscriber, eventName);
        if (info == null) {
            return;
        }
        IEventListener<T> subscribedTo = (IEventListener<T>) info.getSubscriber();
        long delayMs = info.isDelayed() ? this.deliveryDelayMs : 0;
        this.submitTask(subscribedTo, obj, eventName, subscriber, delayMs, onlyThis);
    }

    // ── submitTask ──

    /**
     * 提交任务：延时 → 走调度器；立即 → 直接入执行器。
     */
    private <T extends IDomainEvent> void submitTask(
            IEventListener<T> subscriber, T event,
            String eventName, String alias,
            long delayMs, boolean onlyThis) {

        Task<T> task = new Task<>(subscriber, event,
                this.maxRetryTimes, this.retryDelayMs,
                this.delayScheduler, this.taskExecutor,
                s -> {
                    if (this.orderManager != null && !onlyThis) {
                        List<String> nextSubscribers = this.orderManager
                                .findNextSubscribers(eventName, alias);
                        nextSubscribers.forEach(ss -> this.publish(event, ss, false));
                    }
                });

        if (delayMs > 0) {
            this.delayScheduler.schedule(
                    () -> this.taskExecutor.execute(task),
                    delayMs, TimeUnit.MILLISECONDS);
        } else {
            this.taskExecutor.execute(task);
        }
    }

    // ── 生命周期 ──

    @Override
    public void shutdown() {
        log.info("Shutting down ThreadPoolEventManager");

        // 先停调度器，不再接受新的延时任务
        this.delayScheduler.shutdown();
        // 再停执行器
        this.taskExecutor.shutdown();

        try {
            if (!this.delayScheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                this.delayScheduler.shutdownNow();
            }
            if (!this.taskExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                this.taskExecutor.shutdownNow();
                log.warn("Task executor did not terminate in time, forced shutdown");
            }
        } catch (InterruptedException e) {
            this.delayScheduler.shutdownNow();
            this.taskExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @Deprecated
    public void close() {
        shutdown();
    }
}
