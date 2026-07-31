package io.pragmatic.ddd.rules;

import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * 规则激活条件 —— 决定一条规则在特定模型上下文中是否参与校验。
 *
 * @author wizard-lee
 */
@FunctionalInterface
public interface IActiveRuleCondition<T> {

    /**
     * 返回规则在给定模型下的激活状态。
     *
     * @param newModel 当前被校验的模型
     * @param oldModel 修改前的模型快照，不存在时为 null
     */
    ActiveStatus status(T newModel, T oldModel);

    /** 仅关心当前模型时的便捷入口。 */
    default ActiveStatus status(T newModel) {
        return status(newModel, null);
    }

    /** 不需要旧实体时的便捷适配（状态由单参数推导）。 */
    static <T> IActiveRuleCondition<T> of(Function<T, ActiveStatus> singleArgCondition) {
        return (newModel, oldModel) -> singleArgCondition.apply(newModel);
    }

    /** 需要旧实体时的便捷适配（状态由新旧双参数推导）。 */
    static <T> IActiveRuleCondition<T> of(BiFunction<T, T, ActiveStatus> doubleArgCondition) {
        return doubleArgCondition::apply;
    }
}
