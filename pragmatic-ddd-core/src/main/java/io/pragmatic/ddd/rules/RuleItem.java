package io.pragmatic.ddd.rules;

import io.pragmatic.ddd.base.ICheckRule;
import io.pragmatic.ddd.base.MessageCode;
import lombok.Getter;

/**
 * 规则项 —— 将一条校验项与其消息码、激活条件封装为可被插入/替换/移除的单元。
 *
 * @author wizard-lee
 */
@Getter
public class RuleItem<T> {
    /**
     * -- GETTER --
     * 返回校验项。
     */
    private final ICheckRule<T> rule;
    /**
     * -- GETTER --
     * 返回激活条件。
     */
    private final IActiveRuleCondition<T> condition;
    /**
     * -- GETTER --
     * 返回消息码。
     */
    private final MessageCode messageCode;

    /** 构造规则项。 */
    public RuleItem(ICheckRule<T> rule, MessageCode messageCode, IActiveRuleCondition<T> condition) {
        this.rule = rule;
        this.messageCode = messageCode;
        this.condition = condition;
    }

}
