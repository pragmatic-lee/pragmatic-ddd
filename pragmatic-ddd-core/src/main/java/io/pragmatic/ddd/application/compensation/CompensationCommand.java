package io.pragmatic.ddd.application.compensation;

import io.pragmatic.ddd.base.AggregateRoot;
import io.pragmatic.ddd.base.IExternalRequirement;

import java.util.Optional;

/**
 * 待执行补偿命令：由解析器依据聚合声明的外部需求创建，交给 ICompensationScope 执行与登记。
 * 构造时即确定幂等键、路由键与持久化载荷（载荷在正向执行之前求值，WAL 语义要求）。
 *
 * @param <A>   所属聚合根类型
 * @param <REQ> 需求类型（IExternalRequirement 的具体 record 实现）
 * @param <R>   正向产出类型
 * @author wizard-lee
 */
public final class CompensationCommand<A extends AggregateRoot<?>, REQ extends IExternalRequirement, R> {

    private final String actionKey;
    private final String handler;
    private final String payload;
    private final ICompensableAction<A, REQ, R> action;
    private final A aggregateRoot;
    private final REQ requirement;

    /**
     * 构造待执行补偿命令。
     *
     * @param action        可补偿动作
     * @param aggregateRoot 所属聚合根
     * @param requirement   外部需求
     */
    public CompensationCommand(ICompensableAction<A, REQ, R> action,
                               A aggregateRoot,
                               REQ requirement) {
        this.action = action;
        this.aggregateRoot = aggregateRoot;
        this.requirement = requirement;
        this.actionKey = ActionKeys.of(aggregateRoot, requirement.code());
        this.handler = requirement.code();
        this.payload = action.payload(aggregateRoot, requirement);
    }

    /** 正向执行。 */
    public R execute() {
        return action.execute(aggregateRoot, requirement);
    }

    /** 回写聚合。 */
    public void apply(R result) {
        action.apply(aggregateRoot, result);
    }

    /** 逆向补偿。 */
    public void compensate(R result) {
        action.compensate(aggregateRoot, result);
    }

    /** 幂等键，格式 {聚合类型}:{聚合标识}:{需求编码}。 */
    public String actionKey() {
        return actionKey;
    }

    /** 补偿执行器路由键，等于需求编码（requirement.code()）。 */
    public String handler() {
        return handler;
    }

    /** 持久化载荷（正向执行前确定）。 */
    public String payload() {
        return payload;
    }

    /** 本命令的重试策略；empty 表示采用补偿范围配置的默认策略。 */
    public Optional<CompensationPolicy> policy() {
        return action.policy();
    }

    /** 所属聚合根（IConfirmableAction 回调时使用）。 */
    public A aggregateRoot() {
        return aggregateRoot;
    }

    /** 所属动作（Confirm 分派时使用）。 */
    public ICompensableAction<A, REQ, R> action() {
        return action;
    }
}
