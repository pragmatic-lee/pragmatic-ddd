package io.pragmatic.ddd.visual.rule;

/**
 * 规则描述符 —— 承载单个规则的键、描述及是否带条件。
 *
 * @author wizard-lee
 */
public class RuleDescriptor {

    private final String ruleKey;
    private final String ruleDescription;

    private final boolean withConditionRule;

    /** 构造规则描述符。 */
    public RuleDescriptor(String ruleKey, String ruleDescription,boolean withConditionRule) {
        this.ruleKey = ruleKey;
        this.ruleDescription = ruleDescription;
        this.withConditionRule = withConditionRule;
    }

    /** 返回规则键。 */
    public String getRuleKey() {
        return ruleKey;
    }

    /** 返回规则描述。 */
    public String getRuleDescription() {
        return ruleDescription;
    }

    /** 是否带激活条件。 */
    public boolean isWithConditionRule() {
        return withConditionRule;
    }
}
