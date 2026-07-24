package io.pragmatic.ddd.rules;

import io.pragmatic.ddd.base.IRule;

public abstract class BaseRuleValidator<T> {

    protected abstract boolean validate(T model);


    public IRule<T> rule() {
        return this::validate;
    }

    public IActiveRuleCondition<T> ruleCondition() {
        return model -> ActiveStatus.ACTIVE;
    }
}
