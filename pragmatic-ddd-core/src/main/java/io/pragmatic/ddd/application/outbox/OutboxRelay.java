package io.pragmatic.ddd.application.outbox;

import io.pragmatic.ddd.application.outbox.spi.IOutboxStore;
import io.pragmatic.ddd.event.IDomainEvent;
import io.pragmatic.ddd.event.spi.IEventManager;
import io.pragmatic.ddd.event.spi.IEventSerializer;

import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 兜底轮询器（Relay 路径）：认领超时 PENDING 记录补偿重发，失败释放并递增重试，超限转死信。
 *
 * <p>轮询任务对异常分层隔离：调度入口捕获并记录 {@link Throwable}，避免 {@code scheduleAtFixedRate}
 * 因单次异常永久抑制后续执行；认领失败与单条消息失败各自记录，互不影响。</p>
 *
 * @author wizard-lee
 */
public class OutboxRelay {

    private static final Logger LOGGER = Logger.getLogger(OutboxRelay.class.getName());

    private final IOutboxStore outboxStore;
    private final IEventManager eventManager;
    private final IEventSerializer serializer;
    private final ScheduledExecutorService scheduler;
    private final OutboxRelayConfig config;

    /** 通过构造函数一次性注入协作者与运行配置（不可变，线程安全）。 */
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
                this::pollOnceSafely,
                config.pollInterval().toSeconds(),
                config.pollInterval().toSeconds(),
                TimeUnit.SECONDS);
    }

    private void pollOnceSafely() {
        try {
            pollOnce();
        } catch (Throwable t) {
            // 调度边界兜底：异常必须落日志，且不能让周期任务本身终止
            LOGGER.log(Level.SEVERE, "outbox relay poll failed unexpectedly", t);
        }
    }

    /** 单次轮询补偿。 */
    public void pollOnce() {
        List<OutboxMessage> claimed;
        try {
            // 原子认领一批：UPDATE ... SET status=PROCESSING WHERE status=PENDING AND created_at<now-grace LIMIT ?
            claimed = outboxStore.claimPending(config.batchSize(), config.grace());
        } catch (RuntimeException e) {
            LOGGER.log(Level.WARNING, "outbox relay claimPending failed", e);
            return;
        }
        for (OutboxMessage msg : claimed) {
            publishOne(msg);
        }
    }

    private void publishOne(OutboxMessage msg) {
        try {
            IDomainEvent evt = serializer.deserialize(msg.getPayload(), resolveType(msg));
            eventManager.publish(evt);                         // 复用现有投递（事务外）
            outboxStore.markSent(msg.getId());
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "outbox relay publish failed: " + msg.getId(), e);
            handleFailure(msg);
        }
    }

    private void handleFailure(OutboxMessage msg) {
        try {
            outboxStore.release(msg.getId());                  // 释放回 PENDING
            if (outboxStore.incrementAttempts(msg.getId()) > config.maxAttempts()) {
                outboxStore.markFailed(msg.getId());           // 死信
            }
        } catch (RuntimeException e) {
            // 记账失败时保留 PROCESSING，待后续租约回收兜底；异常必须落日志
            LOGGER.log(Level.WARNING, "outbox relay failure bookkeeping failed: " + msg.getId(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private Class<? extends IDomainEvent> resolveType(OutboxMessage msg) throws ClassNotFoundException {
        return (Class<? extends IDomainEvent>) Class.forName(msg.getEventType());
    }
}
