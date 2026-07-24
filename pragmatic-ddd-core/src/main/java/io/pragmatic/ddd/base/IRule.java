package io.pragmatic.ddd.base;

/**
 * 规则接口 — 核心抽象。
 *
 * <p>任何可以对模型执行校验并通过/失败判定的对象都是 IRule。
 * 此接口位于 base 层，EntityRule 等在 rules 层实现该接口，
 * 使核心抽象（BrokenRuleObject）可以依赖本接口而无需反向依赖 rules 包。</p>
 *
 * @param <T> 校验目标类型
 * @see io.pragmatic.ddd.rules.EntityRule
 * @since 2.1.0
 */
public interface IRule<T> {

    /**
     * 判断模型是否满足此规则。
     *
     * @param model 被校验的模型对象
     * @return true 表示通过校验，false 表示存在规则违反
     */
    boolean satisfiesRule(T model);
}
