package io.pragmatic.ddd.rules;

import io.pragmatic.ddd.base.IRule;

public class RuleItem<T> {
    private IRule<T> rule;
    private IParamRule<T> paramRule;
    private final IActiveRuleCondition<T> condition;
    private final String messageKey;
    private final String alias;


    public RuleItem(IRule<T> rule, String messageKey, String alias, IActiveRuleCondition<T> condition) {
        this.rule = rule;
        this.messageKey = messageKey;
        this.alias = alias;
        this.condition = condition;
    }

    public RuleItem(IParamRule<T> paramRule, String messageKey, String alias, IActiveRuleCondition<T> condition) {
        this.paramRule = paramRule;
        this.messageKey = messageKey;
        this.alias = alias;
        this.condition = condition;
    }

    public IRule<T> getRule() {
        return rule;
    }

    public IParamRule<T> getParamRule() {
        return this.paramRule;
    }

    public IActiveRuleCondition<T> getCondition() {
        return this.condition;
    }
    public String getMessageKey() {
        return messageKey;
    }

    public String getAlias() {
        return alias;
    }
}
