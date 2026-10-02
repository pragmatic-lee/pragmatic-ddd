package io.pragmatic.ddd.application.compensation;

/**
 * 补偿动作契约：一个"正向调用外部系统 + 逆向回滚外部系统"的配对单元。
 * 正向产出（预占单号、锁令牌等）由实现自身持有，供 compensate 使用。
 *
 * @param <T> 正向产出类型
 * @author wizard-lee
 */
public interface ICompensableAction<T> {

    /**
     * 业务幂等键：补偿去重、日志定位与人工排查的唯一标识。
     * 必须在一次补偿范围内唯一；同一动作在一次调用中可能执行多次时，
     * 须由调用方追加区分符（序号、外部返回号）保证唯一，
     * 例如 order:SO20260927001:reserve-inventory:1。
     *
     * @return 幂等键
     */
    String actionKey();

    /** 正向执行：调用外部系统完成写入 / 预占 / 锁定，返回供本地聚合使用的产出。 */
    T execute();

    /** 逆向补偿：调用外部系统回滚接口，必须幂等。 */
    void compensate(T result);

    /** 补偿重试策略，默认 3 次 / 200ms。 */
    default CompensationPolicy policy() {
        return CompensationPolicy.defaultPolicy();
    }
}
