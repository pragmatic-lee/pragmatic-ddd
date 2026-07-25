package io.pragmatic.ddd.rules;

import io.pragmatic.ddd.base.IRule;
import io.pragmatic.ddd.base.MessageCode;

public class RuleItem<T> {
    private IRule<T> rule;
    private IParamRule<T> paramRule;
    private final IActiveRuleCondition<T> condition;
    private final MessageCode messageCode;


    public RuleItem(IRule<T> rule, MessageCode messageCode, IActiveRuleCondition<T> condition) {
        this.rule = rule;
        this.messageCode = messageCode;
        this.condition = condition;
    }

    public RuleItem(IParamRule<T> paramRule, MessageCode messageCode, IActiveRuleCondition<T> condition) {
        this.paramRule = paramRule;
        this.messageCode = messageCode;
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

    public MessageCode getMessageCode() {
        return messageCode;
    }
}
