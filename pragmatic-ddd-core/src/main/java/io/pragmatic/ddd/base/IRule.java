package io.pragmatic.ddd.base;

/**
 * 规则接口，核心抽象。
 * 任何可对模型执行校验并判定通过/失败的契约；位于 base 层，供 rules 包实现，避免核心反向依赖。
 *
 * @param <T> 校验目标类型
 * @author wizard-lee
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
