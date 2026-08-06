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
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 基于线程池的本地事件管理器。
 * 采用"调度器 + 执行器分离"架构：延时任务由调度器等待，到期后提交执行器处理。
 *
 * @author wizard-lee
 */
public class ThreadPoolEventManager extends AbstractEventManager {

    private static final Logger log = LoggerFactory.getLogger(ThreadPoolEventManager.class);

    private final ScheduledExecutorService delayScheduler;
    private final ThreadPoolExecutor taskExecutor;
    private final int maxRetryTimes;
    private final int retryDelayMs;
    private final int deliveryDelayMs;

    // ── 构造器 ──

    public ThreadPoolEventManager() {
        this(LocalEventManagerConfig.defaultConfig(), new SubscriberOrderManager());
    }

    public ThreadPoolEventManager(
            int corePoolSize, int maxPoolSize, int queueCapacity,
            int maxRetryTimes, int retryDelayMs, int deliveryDelayMs,
            ISubscriberOrderManager orderManager) {
        this(new LocalEventManagerConfig(
                2, corePoolSize, maxPoolSize, queueCapacity,
                60, deliveryDelayMs, maxRetryTimes, retryDelayMs), orderManager);
    }

    /**
     * 基于类型化配置构造（新增，兼容并收敛既有常量）。
     *
     * @param config       运行配置（可由 LocalEventManagerConfig.bind 从配置源加载）
     * @param orderManager 订阅者顺序管理器
     */
    public ThreadPoolEventManager(LocalEventManagerConfig config, ISubscriberOrderManager orderManager) {
        super(orderManager);
        this.maxRetryTimes = config.maxRetryTimes();
        this.retryDelayMs = config.retryDelayMs();
        this.deliveryDelayMs = config.deliveryDelayMs();

        // 调度器：只负责等待延时，不执行业务逻辑
        this.delayScheduler = new ScheduledThreadPoolExecutor(
                config.schedulerThreads(),
                createThreadFactory("domain-event-scheduler-"));

        // 执行器：有界队列 + CallerRunsPolicy，背压保护
        this.taskExecutor = new ThreadPoolExecutor(
                config.corePoolSize(),
                config.maxPoolSize(),
                config.keepAliveSeconds(),
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(config.queueCapacity()),
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
        String eventName = obj.getClass().getSimpleName();
        Map<String, SubscriberInfo> subscriberMap = this.filterSubscriberInfoMap(eventName);

        for (Map.Entry<String, SubscriberInfo> entry : subscriberMap.entrySet()) {
            IEventListener<T> subscribedTo = (IEventListener<T>) entry.getValue().subscriber();
            String alias = entry.getKey();
            // 第一重：订阅者级开关（外部动态配置决定是否启用该订阅者）
            if (this.switchCheck(alias, entry.getValue().condition()) == ExecuteStatus.SKIP) {
                continue;
            }
            // 第二重：事件级条件（基于事件内容决定是否执行）
            if (subscribedTo != null
                    && this.executeCheck(obj, entry.getValue().condition()) == ExecuteStatus.EXECUTE) {
                long delayMs = entry.getValue().isDelayed() ? this.deliveryDelayMs : 0;
                this.submitTask(subscribedTo, obj, eventName, alias, delayMs, false);
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
        String eventName = obj.getClass().getSimpleName();
        SubscriberInfo info = this.findSubscriberInfo(obj, subscriber, eventName);
        if (info == null) {
            return;
        }
        IEventListener<T> subscribedTo = (IEventListener<T>) info.subscriber();
        long delayMs = info.isDelayed() ? this.deliveryDelayMs : 0;
        this.submitTask(subscribedTo, obj, eventName, subscriber, delayMs, onlyThis);
    }

    // ── submitTask ──

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
