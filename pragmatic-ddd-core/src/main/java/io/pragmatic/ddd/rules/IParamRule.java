package io.pragmatic.ddd.rules;

/**
 * 参数化业务规则 —— 校验结果携带动态消息参数。
 *
 * @author wizard-lee
 */
public interface IParamRule<T> {

    /** 对模型执行校验，返回带参数的结果。 */
    RuleCheckResult isSatisfy(T model);

}
