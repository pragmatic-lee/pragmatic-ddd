package io.pragmatic.ddd.rules;

import io.pragmatic.ddd.base.IRule;

/**
 * 规则校验器基类 —— 将 {@code validate(T)} 适配为 {@link IRule} 与激活条件。
 *
 * @author wizard-lee
 */
public abstract class BaseRuleValidator<T> {

    /** 子类实现具体的校验逻辑。 */
    protected abstract boolean validate(T model);

    /** 包装校验逻辑为 {@link IRule}。 */
    public IRule<T> rule() {
        return this::validate;
    }

    /** 返回默认激活条件（始终生效）。 */
    public IActiveRuleCondition<T> ruleCondition() {
        return model -> ActiveStatus.ACTIVE;
    }
}
