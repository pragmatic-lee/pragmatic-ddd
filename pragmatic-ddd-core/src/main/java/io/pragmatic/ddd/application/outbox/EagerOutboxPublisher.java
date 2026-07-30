package io.pragmatic.ddd.application.outbox;

import io.pragmatic.ddd.application.outbox.spi.IOutboxStore;
import io.pragmatic.ddd.event.spi.IEventManager;

import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * 提交后主动推送器（eager 路径）：事务提交后异步发送原始事件，
 * 成功标记 SENT，失败/崩溃则不标记，保持 PENDING 交由 Relay 补偿。
 *
 * @author wizard-lee
 */
public class EagerOutboxPublisher {

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

    /** 事务提交后调用：异步发送每一条原始事件（markSent 为独立短事务，MQ 发送在事务外）。 */
    public void publishAfterCommit(List<OutboxEntry> entries) {
        for (OutboxEntry entry : entries) {
            pool.submit(() -> {
                try {
                    eventManager.publish(entry.event());              // 直接发原始事件，省去反序列化
                    outboxStore.markSent(entry.message().getId());    // PENDING→SENT（带 status 守卫，幂等）
                } catch (Exception e) {
                    // 失败不标记，保留 PENDING，交由兜底轮询补偿
                }
            });
        }
    }
}
