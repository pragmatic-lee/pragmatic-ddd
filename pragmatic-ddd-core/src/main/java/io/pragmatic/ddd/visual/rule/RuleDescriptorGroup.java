package io.pragmatic.ddd.visual.rule;

import java.util.List;

public class RuleDescriptorGroup {

    private final String name;

    private final List<RuleDescriptor> ruleDescriptorList;

    public RuleDescriptorGroup(String name, List<RuleDescriptor> ruleDescriptorList) {
        this.name = name;
        this.ruleDescriptorList = ruleDescriptorList;
    }

    public String getName() {
        return name;
    }

    public List<RuleDescriptor> getRuleDescriptorList() {
        return ruleDescriptorList;
    }
}
