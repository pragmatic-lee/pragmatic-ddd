package io.pragmatic.ddd.application.outbox.spi;

import io.pragmatic.ddd.application.outbox.OutboxMessage;

import java.time.Duration;
import java.util.List;

/**
 * Outbox 存储 SPI（落库 / 认领 / 标记），由基础设施模块（如 pragmatic-ddd-mybatis）提供实现。
 * 约束：store 在调用方事务内执行；claim/markSent/release/claimPending/incrementAttempts/markFailed 各自为独立短事务且不含 MQ 发送；markSent 带 status 守卫以实现幂等。
 *
 * @author wizard-lee
 */
public interface IOutboxStore {

    /** 同事务批量落库（PENDING）。 */
    void store(List<OutboxMessage> messages);

    /** 单条认领：PENDING → PROCESSING。 */
    OutboxMessage claim(String id);

    /** 标记发送成功：PENDING/PROCESSING → SENT（带 status 守卫，幂等）。 */
    void markSent(String id);

    /** 释放回 PENDING：PROCESSING → PENDING。 */
    void release(String id);

    /** 原子认领一批超时 PENDING 记录（age > grace）。 */
    List<OutboxMessage> claimPending(int batchSize, Duration grace);

    /** 递增重试次数，返回递增后的新值。 */
    int incrementAttempts(String id);

    /** 标记死信：→ FAILED（重试耗尽）。 */
    void markFailed(String id);
}
