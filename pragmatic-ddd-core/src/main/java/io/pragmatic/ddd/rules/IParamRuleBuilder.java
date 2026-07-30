package io.pragmatic.ddd.rules;

/**
 * 参数化规则构造器 —— 将 {@code validate(T)} 适配为 {@link IParamRule} 与激活条件。
 *
 * @author wizard-lee
 */
public interface IParamRuleBuilder<T> {

    /** 包装校验逻辑为 {@link IParamRule}。 */
    IParamRule<T> rule();

    /** 返回激活条件；默认 null 表示使用上层默认条件。 */
    default IActiveRuleCondition<T> ruleCondition() {
        return null;
    }
}
