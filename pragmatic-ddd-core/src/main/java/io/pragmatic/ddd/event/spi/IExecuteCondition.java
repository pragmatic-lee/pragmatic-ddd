package io.pragmatic.ddd.event.spi;

import io.pragmatic.ddd.event.IDomainEvent;

/**
 * 事件执行条件端口，决定某订阅者是否处理给定事件。
 *
 * @param <T> 领域事件类型
 * @author wizard-lee
 */
public interface IExecuteCondition<T extends IDomainEvent> {
    /** 返回该事件是否应当被处理。 */
    ExecuteStatus status(T t);

    /**
     * 按订阅者别名判断该订阅者是否需要启用（订阅者级开关）。
     *
     * <p>与 {@link #status} 的职责不同：本方法基于「订阅者标识」而非「事件内容」判断，
     * 常被用于读取外部动态配置（如配置中心、开关平台）决定临时启用/停用某个订阅者。
     * 实现可以不保持纯函数语义。</p>
     *
     * <p>默认实现返回 {@link ExecuteStatus#EXECUTE}，即默认执行；既有实现无需覆盖即可获得
     * 与原先完全一致的行为。</p>
     *
     * @param alias 订阅者别名
     * @return EXECUTE 表示启用，SKIP 表示停用
     */
    default ExecuteStatus switchStatus(String alias) {
        return ExecuteStatus.EXECUTE;
    }
}
