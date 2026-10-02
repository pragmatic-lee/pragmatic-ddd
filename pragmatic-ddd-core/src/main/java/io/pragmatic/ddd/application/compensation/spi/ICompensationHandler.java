package io.pragmatic.ddd.application.compensation.spi;

/**
 * 补偿执行器：把一条持久化的补偿记录还原为一次补偿调用。
 * 业务侧为每个需要"崩溃后兜底"的外部动作各提供一个实现。
 *
 * @author wizard-lee
 */
public interface ICompensationHandler {

    /** 本执行器负责的动作名，与 CompensationRecord.handler 一致，全局唯一。 */
    String actionName();

    /** 用持久化的业务标识（payload）还原并执行补偿，必须幂等。 */
    void compensate(String payload);
}
