package io.pragmatic.ddd.application.outbox;

import io.pragmatic.ddd.application.outbox.spi.IOutboxStore;
import io.pragmatic.ddd.event.spi.IEventManager;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 提交后主动推送器（eager 路径）：事务提交后异步发送原始事件，
 * 成功标记 SENT，失败/崩溃则不标记，保持 PENDING 交由 Relay 补偿。
 *
 * <p>提交后路径保证不向调用方抛异常：本类任何失败都只保留 PENDING 交 Relay 兜底，
 * 不冒泡进补偿范围（补偿范围的成功终点为本地事务提交）。</p>
 *
 * @author wizard-lee
 */
public class EagerOutboxPublisher {

    private static final Logger LOGGER = Logger.getLogger(EagerOutboxPublisher.class.getName());

    private final IOutboxStore outboxStore;
    private final IEventManager eventManager;
    private final ExecutorService pool;   // 有界线程池

    public EagerOutboxPublisher(IOutboxStore outboxStore,
                                IEventManager eventManager,
                                ExecutorService pool) {
        this.outboxStore = outboxStore;
        this.eventManager = eventManager;
        this.pool = pool;
    }

    /**
     * 事务提交后调用：异步发送每一条原始事件（markSent 为独立短事务，MQ 发送在事务外）。
     * 提交动作本身失败（典型为线程池饱和 / 已关闭）时同样不抛异常，仅保留 PENDING。
     *
     * @param entries 待推送的 outbox 条目
     */
    public void publishAfterCommit(List<OutboxEntry> entries) {
        for (OutboxEntry entry : entries) {
            submitQuietly(entry);
        }
    }

    private void submitQuietly(OutboxEntry entry) {
        try {
            pool.submit(() -> {
                try {
                    eventManager.publish(entry.event());              // 直接发原始事件，省去反序列化
                    outboxStore.markSent(entry.message().getId());    // PENDING→SENT（带 status 守卫，幂等）
                } catch (Exception e) {
                    // 失败不标记，保留 PENDING，交由兜底轮询补偿
                }
            });
        } catch (RuntimeException e) {
            // 提交动作失败，典型为有界线程池饱和 / 已关闭时的 RejectedExecutionException。
            // 不标记，保留 PENDING，交由 Relay 兜底补偿。
            LOGGER.log(Level.WARNING,
                    "outbox eager publish rejected, fallback to relay: " + entry.message().getId(), e);
        }
    }
}
