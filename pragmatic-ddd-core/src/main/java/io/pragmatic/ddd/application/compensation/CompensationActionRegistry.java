package io.pragmatic.ddd.application.compensation;

import io.pragmatic.ddd.base.AggregateRoot;
import io.pragmatic.ddd.base.IExternalRequirement;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 补偿动作注册表：按 requirementType()（Class，与事件体系的 subscribedToEventType() 同款路由方式）
 * 建索引，注册期查重、解析期查存在与聚合类型，全部显式失败（不静默跳过、不静默降级）。
 *
 * <p>所有聚合共用同一份：由应用层装配以动作集合构造后，注入 {@link CompensableCommandExecutor}；
 * 各聚合只需把自己的动作声明为组件，装配层统一收集即可。</p>
 *
 * @author wizard-lee
 */
public final class CompensationActionRegistry {

    private final Map<Class<? extends IExternalRequirement>, ICompensableAction<?, ?, ?>> actions;

    /**
     * 以动作集合构造注册表，需求类型重复时立即失败。
     *
     * @param actions 可补偿动作集合（按需求类型索引，重复类型构造即失败）
     */
    public CompensationActionRegistry(List<ICompensableAction<?, ?, ?>> actions) {
        // toMap 遇重复类型抛 IllegalStateException：fail-fast
        this.actions = actions.stream()
                .collect(Collectors.toMap(ICompensableAction::requirementType, Function.identity()));
    }

    /**
     * 解析一项外部需求。
     *
     * @param requirement   外部需求（强类型 record 实例）
     * @param aggregateRoot 声明该需求的聚合
     * @param <A>           聚合类型
     * @param <R>           正向产出类型
     * @return 待执行补偿命令；无匹配动作或聚合类型不匹配时显式失败
     */
    @SuppressWarnings("unchecked")
    <A extends AggregateRoot<?>, R> CompensationCommand<A, ?, R> resolve(
            IExternalRequirement requirement, A aggregateRoot) {
        ICompensableAction<?, ?, ?> action = this.actions.get(requirement.getClass());
        if (action == null) {
            throw new IllegalStateException("no compensable action for requirement: " + requirement.code());
        }
        if (!action.aggregateType().isInstance(aggregateRoot)) {
            throw new IllegalStateException("action for requirement " + requirement.code()
                    + " cannot handle aggregate " + aggregateRoot.getClass().getName());
        }
        // 以 IExternalRequirement 作类型见证，绕开 wildcard capture 无法做钻石推断的问题；
        // 需求实际类型已由 requirement.getClass() 索引命中，运行期强类型安全。
        ICompensableAction<A, IExternalRequirement, R> typedAction =
                (ICompensableAction<A, IExternalRequirement, R>) action;
        return new CompensationCommand<>(typedAction, aggregateRoot, requirement);
    }
}
