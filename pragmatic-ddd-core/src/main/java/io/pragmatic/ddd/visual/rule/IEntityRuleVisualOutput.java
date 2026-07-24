package io.pragmatic.ddd.visual.rule;

import java.util.List;

public interface IEntityRuleVisualOutput {
    String output(List<RuleDescriptorGroup> group);
}
