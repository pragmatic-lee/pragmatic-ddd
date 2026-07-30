package io.pragmatic.ddd.visual.rule;

import java.util.List;

/**
 * 规则描述符分组 —— 承载一组同名的规则描述符。
 *
 * @author wizard-lee
 */
public class RuleDescriptorGroup {

    private final String name;

    private final List<RuleDescriptor> ruleDescriptorList;

    /** 构造规则描述符分组。 */
    public RuleDescriptorGroup(String name, List<RuleDescriptor> ruleDescriptorList) {
        this.name = name;
        this.ruleDescriptorList = ruleDescriptorList;
    }

    /** 返回分组名称。 */
    public String getName() {
        return name;
    }

    /** 返回规则描述符列表。 */
    public List<RuleDescriptor> getRuleDescriptorList() {
        return ruleDescriptorList;
    }
}
