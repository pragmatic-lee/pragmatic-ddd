package io.pragmatic.ddd.visual.rule;

public class RuleDescriptor {

    private final String ruleKey;
    private final String ruleDescription;

    private final boolean withConditionRule;

    public RuleDescriptor(String ruleKey, String ruleDescription,boolean withConditionRule) {
        this.ruleKey = ruleKey;
        this.ruleDescription = ruleDescription;
        this.withConditionRule = withConditionRule;
    }

    public String getRuleKey() {
        return ruleKey;
    }

    public String getRuleDescription() {
        return ruleDescription;
    }

    public boolean isWithConditionRule() {
        return withConditionRule;
    }
}
