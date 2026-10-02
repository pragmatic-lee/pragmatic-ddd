package io.pragmatic.ddd.application.compensation.spi;

import java.util.List;

/**
 * 持久化补偿日志 SPI（L3）：以写前日志（WAL）保证崩溃后可恢复补偿。
 * 全部写方法须使用 REQUIRES_NEW 独立短事务。
 *
 * @author wizard-lee
 */
public interface ICompensationLog {

    /** 正向执行前登记（状态 PENDING），须在外部调用之前落库。 */
    void record(CompensationRecord record);

    /** 正向执行成功后标记为待补偿（EXECUTED）。 */
    void markExecuted(String actionKey);

    /**
     * 补偿前原子认领：仅当记录仍处 EXECUTED 时置 COMPENSATING 并返回 true，
     * 否则返回 false（已被其他实例认领）。多实例并发安全。
     *
     * @param actionKey  幂等键
     * @param claimToken 认领令牌
     * @return 是否认领成功
     */
    boolean markCompensating(String actionKey, String claimToken);

    /** 补偿成功后标记完成（COMPENSATED）。 */
    void markCompensated(String actionKey);

    /** 补偿失败后标记失败（FAILED）并累加尝试次数。 */
    void markFailed(String actionKey, String reason);

    /** 查询待补偿记录（status = EXECUTED），供中继自动重试。 */
    List<CompensationRecord> findExecuted(int limit);

    /** 查询悬挂记录（status = PENDING，正向结果未知），供对账与人工排查，不得自动补偿。 */
    List<CompensationRecord> findSuspended(int limit);

    /** 查询补偿失败记录（status = FAILED），供人工介入与死信处理。 */
    List<CompensationRecord> findFailed(int limit);
}
