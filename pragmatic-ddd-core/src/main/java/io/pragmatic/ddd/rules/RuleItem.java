package io.pragmatic.ddd.rules;

import io.pragmatic.ddd.base.IRule;
import io.pragmatic.ddd.base.MessageCode;

/**
 * 规则项 —— 将一条规则与其消息码、激活条件封装为可被插入/替换/移除的单元。
 *
 * @author wizard-lee
 */
public class RuleItem<T> {
    private IRule<T> rule;
    private IParamRule<T> paramRule;
    private final IActiveRuleCondition<T> condition;
    private final MessageCode messageCode;


    /** 构造普通规则项。 */
    public RuleItem(IRule<T> rule, MessageCode messageCode, IActiveRuleCondition<T> condition) {
        this.rule = rule;
        this.messageCode = messageCode;
        this.condition = condition;
    }

    /** 构造参数化规则项。 */
    public RuleItem(IParamRule<T> paramRule, MessageCode messageCode, IActiveRuleCondition<T> condition) {
        this.paramRule = paramRule;
        this.messageCode = messageCode;
        this.condition = condition;
    }

    /** 返回普通规则。 */
    public IRule<T> getRule() {
        return rule;
    }

    /** 返回参数化规则（可能为 null）。 */
    public IParamRule<T> getParamRule() {
        return this.paramRule;
    }

    /** 返回激活条件。 */
    public IActiveRuleCondition<T> getCondition() {
        return this.condition;
    }

    /** 返回消息码。 */
    public MessageCode getMessageCode() {
        return messageCode;
    }
}
