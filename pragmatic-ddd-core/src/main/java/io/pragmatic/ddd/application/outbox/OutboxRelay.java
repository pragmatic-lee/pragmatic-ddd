package io.pragmatic.ddd.application.outbox;

import io.pragmatic.ddd.application.outbox.spi.IOutboxStore;
import io.pragmatic.ddd.event.IDomainEvent;
import io.pragmatic.ddd.event.spi.IEventManager;
import io.pragmatic.ddd.event.spi.IEventSerializer;

import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 兜底轮询器（Relay 路径，MVP 单队列）。
 *
 * <p>认领 {@code age > grace} 的 PENDING 记录补偿重发；发送成功标记 SENT，失败释放回 PENDING
 * 并递增 attempts，超过 maxAttempts 转 FAILED（死信）。</p>
 *
 * <p>全部字段 {@code final}（含 {@link OutboxRelayConfig}），构造后不可变、线程安全。</p>
 *
 * @author Li XiaoJing
 * @since 2.2.0
 */
public class OutboxRelay {

    private final IOutboxStore outboxStore;
    private final IEventManager eventManager;
    private final IEventSerializer serializer;
    private final ScheduledExecutorService scheduler;
    private final OutboxRelayConfig config;

    /**
     * 通过构造函数一次性注入协作者与运行配置（不可变，线程安全）。
     *
     * @param outboxStore outbox 存储
     * @param eventManager 事件投递（复用现有）
     * @param serializer   事件反序列化
     * @param scheduler    轮询调度器
     * @param config       兜底轮询运行配置（见 {@link OutboxRelayConfig}）
     */
    public OutboxRelay(IOutboxStore outboxStore,
                       IEventManager eventManager,
                       IEventSerializer serializer,
                       ScheduledExecutorService scheduler,
                       OutboxRelayConfig config) {
        this.outboxStore = outboxStore;
        this.eventManager = eventManager;
        this.serializer = serializer;
        this.scheduler = scheduler;
        this.config = config;
    }

    /** 启动周期性兜底轮询。 */
    public void start() {
        scheduler.scheduleAtFixedRate(
                this::pollOnce,
                config.pollInterval().toSeconds(),
                config.pollInterval().toSeconds(),
                TimeUnit.SECONDS);
    }

    /** 单次轮询补偿。 */
    public void pollOnce() {
        // 原子认领一批：UPDATE ... SET status=PROCESSING WHERE status=PENDING AND created_at<now-grace LIMIT ?
        List<OutboxMessage> claimed = outboxStore.claimPending(config.batchSize(), config.grace());
        for (OutboxMessage msg : claimed) {
            try {
                IDomainEvent evt = serializer.deserialize(msg.getPayload(), resolveType(msg));
                eventManager.publish(evt);                     // 复用现有投递（事务外）
                outboxStore.markSent(msg.getId());
            } catch (Exception e) {
                outboxStore.release(msg.getId());              // 释放回 PENDING
                if (outboxStore.incrementAttempts(msg.getId()) > config.maxAttempts()) {
                    outboxStore.markFailed(msg.getId());       // 死信
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Class<? extends IDomainEvent> resolveType(OutboxMessage msg) throws ClassNotFoundException {
        return (Class<? extends IDomainEvent>) Class.forName(msg.getEventType());
    }
}
