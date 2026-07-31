package io.pragmatic.ddd.rules;

import io.pragmatic.ddd.base.ICheckRule;
import io.pragmatic.ddd.base.RuleCheckResult;

/**
 * 规则校验器基类 —— 将 {@code validate(T)} 适配为校验项级 {@link ICheckRule} 与激活条件。
 *
 * @author wizard-lee
 */
public abstract class BaseRuleValidator<T> {

    /** 子类实现具体的校验逻辑。 */
    protected abstract boolean validate(T model);

    /** 包装校验逻辑为校验项级契约。 */
    public ICheckRule<T> rule() {
        return model -> RuleCheckResult.of(validate(model));
    }

    /** 返回默认激活条件（始终生效）。 */
    public IActiveRuleCondition<T> ruleCondition() {
        return model -> ActiveStatus.ACTIVE;
    }
}
