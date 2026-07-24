package io.pragmatic.ddd.application.outbox.spi;

import io.pragmatic.ddd.application.outbox.OutboxMessage;

import java.time.Duration;
import java.util.List;

/**
 * Outbox 存储 SPI（落库 / 认领 / 标记）。
 * 由 {@code pragmatic-ddd-mybatis} 等基础设施模块提供实现。
 *
 * <p><b>实现约束（铁律）：</b></p>
 * <ul>
 *   <li>{@link #store} 在调用方事务内执行（与聚合同事务落库）。</li>
 *   <li>{@link #claim}/{@link #markSent}/{@link #release}/{@link #claimPending}/{@link #incrementAttempts}/{@link #markFailed}
 *       各自是<b>独立短事务</b>并立即提交，<b>绝不</b>包裹 MQ 发送。</li>
 *   <li>{@link #markSent} 必须带 {@code WHERE id=? AND status=PENDING} 守卫，
 *       避免覆盖已被 Relay 置为 FAILED 的行（幂等）。</li>
 * </ul>
 *
 * @author Li XiaoJing
 * @since 2.2.0
 */
public interface IOutboxStore {

    /** 同事务批量落库（PENDING）。 */
    void store(List<OutboxMessage> messages);

    /** 单条认领：PENDING → PROCESSING（返回认领后的行，用于单条补偿场景）。 */
    OutboxMessage claim(String id);

    /** 标记发送成功：PENDING/PROCESSING → SENT（带 status 守卫，幂等）。 */
    void markSent(String id);

    /** 释放回 PENDING：PROCESSING → PENDING。 */
    void release(String id);

    /** 原子认领一批：UPDATE ... SET status=PROCESSING
     *  WHERE status=PENDING AND created_at &lt; now-grace LIMIT ? （返回认领后的行）。 */
    List<OutboxMessage> claimPending(int batchSize, Duration grace);

    /** 递增重试次数，返回递增后的新值（由调用方判断是否超 maxAttempts）。 */
    int incrementAttempts(String id);

    /** 标记死信：→ FAILED（重试耗尽）。 */
    void markFailed(String id);
}
