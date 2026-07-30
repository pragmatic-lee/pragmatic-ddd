package io.pragmatic.ddd.visual.rule;

import java.util.List;

/**
 * 实体规则可视化输出 —— 将规则描述符分组列表渲染为文本。
 *
 * @author wizard-lee
 */
public interface IEntityRuleVisualOutput {
    /** 将规则描述符分组列表渲染为文本。 */
    String output(List<RuleDescriptorGroup> group);
}
