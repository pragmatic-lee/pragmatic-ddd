package io.pragmatic.ddd.rules;

import io.pragmatic.ddd.base.MessageCode;

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

    /**
     * 按规则码判断该规则是否需要启用（code 级开关）。
     *
     * <p>与 {@link #status} 的职责不同：本方法基于「规则标识」而非「模型内容」判断，
     * 常被用于读取外部动态配置（如配置中心、开关平台）决定临时启用/停用某条规则。
     * 实现可以不保持纯函数语义。</p>
     *
     * <p>默认实现返回 {@link ActiveStatus#ACTIVE}，即默认启用；既有实现无需覆盖即可获得
     * 与原先完全一致的行为。</p>
     *
     * @param messageCode 当前规则的规则码
     * @return ACTIVE 表示启用，INACTIVE 表示停用
     */
    default ActiveStatus switchStatus(MessageCode messageCode) {
        return ActiveStatus.ACTIVE;
    }

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
