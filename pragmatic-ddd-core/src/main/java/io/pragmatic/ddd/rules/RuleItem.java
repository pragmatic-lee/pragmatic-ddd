package io.pragmatic.ddd.rules;

import io.pragmatic.ddd.base.ICheckRule;
import io.pragmatic.ddd.base.MessageCode;

/**
 * 规则项 —— 将一条校验项与其消息码、激活条件封装为可被插入/替换/移除的单元。
 *
 * @author wizard-lee
 */
public class RuleItem<T> {
    private final ICheckRule<T> rule;
    private final IActiveRuleCondition<T> condition;
    private final MessageCode messageCode;

    /** 构造规则项。 */
    public RuleItem(ICheckRule<T> rule, MessageCode messageCode, IActiveRuleCondition<T> condition) {
        this.rule = rule;
        this.messageCode = messageCode;
        this.condition = condition;
    }

    /** 返回校验项。 */
    public ICheckRule<T> getRule() {
        return rule;
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
