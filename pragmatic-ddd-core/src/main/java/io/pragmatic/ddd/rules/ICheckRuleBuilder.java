package io.pragmatic.ddd.rules;

import io.pragmatic.ddd.rules.ICheckRule;

/**
 * 校验项构造器 —— 将 {@code validate(T)} 适配为 {@link ICheckRule} 与激活条件。
 *
 * @author wizard-lee
 */
public interface ICheckRuleBuilder<T> {

    /** 包装校验逻辑为 {@link ICheckRule}。 */
    ICheckRule<T> rule();

    /** 返回激活条件；默认 null 表示使用上层默认条件。 */
    default IActiveRuleCondition<T> ruleCondition() {
        return null;
    }
}
