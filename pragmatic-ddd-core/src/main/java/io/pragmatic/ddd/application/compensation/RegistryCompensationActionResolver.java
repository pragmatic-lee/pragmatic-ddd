package io.pragmatic.ddd.application.compensation;

import io.pragmatic.ddd.base.AggregateRoot;
import io.pragmatic.ddd.base.IExternalRequirement;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 默认需求解析器：按 requirementType()（Class，与事件体系的 subscribedToEventType() 同款路由方式）
 * 建索引，注册期查重、解析期查存在与聚合类型，全部显式失败（不静默跳过、不静默降级）。
 *
 * @author wizard-lee
 */
public final class RegistryCompensationActionResolver implements ICompensationActionResolver {

    private final Map<Class<? extends IExternalRequirement>, ICompensableAction<?, ?, ?>> actions;

    /**
     * 以动作列表构造解析器，需求类型重复时立即失败。
     *
     * @param actions 可补偿动作列表
     */
    public RegistryCompensationActionResolver(List<ICompensableAction<?, ?, ?>> actions) {
        // toMap 遇重复类型抛 IllegalStateException：fail-fast
        this.actions = actions.stream()
                .collect(Collectors.toMap(ICompensableAction::requirementType, Function.identity()));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <A extends AggregateRoot<?>, R> CompensationCommand<A, ?, R> resolve(
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
