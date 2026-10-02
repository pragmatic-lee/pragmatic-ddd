package io.pragmatic.ddd.application.compensation;

/**
 * 补偿状态机：PENDING → EXECUTED → COMPENSATING → COMPENSATED / FAILED。
 *
 * @author wizard-lee
 */
public enum CompensationStatus {
    /** 正向执行前已登记；若正向结果未知则长期停留于此（悬挂）。 */
    PENDING,
    /** 正向执行成功，等待（可能的）补偿。 */
    EXECUTED,
    /** 补偿进行中（已认领）。 */
    COMPENSATING,
    /** 补偿完成。 */
    COMPENSATED,
    /** 补偿失败，需中继或人工介入。 */
    FAILED
}
