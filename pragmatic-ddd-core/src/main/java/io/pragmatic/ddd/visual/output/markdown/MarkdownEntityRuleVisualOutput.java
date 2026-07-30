package io.pragmatic.ddd.visual.output.markdown;

import io.pragmatic.ddd.visual.rule.IEntityRuleVisualOutput;
import io.pragmatic.ddd.visual.rule.RuleDescriptor;
import io.pragmatic.ddd.visual.rule.RuleDescriptorGroup;
import org.apache.commons.lang3.SystemUtils;

import java.util.List;

/**
 * 实体规则 Markdown 输出 —— 将规则描述符分组列表渲染为 Markdown 表格。
 *
 * @author wizard-lee
 */
public class MarkdownEntityRuleVisualOutput implements IEntityRuleVisualOutput {
    /** 将规则描述符分组列表渲染为 Markdown 文本。 */
    @Override
    public String output(List<RuleDescriptorGroup> group) {

        StringBuilder stringBuilder = new StringBuilder();


        group.forEach(g->{
            String s = this.buildEntityRule(g.getRuleDescriptorList());
            stringBuilder.append(s);
        });

        return stringBuilder.toString();
    }

    private String buildEntityRule(List<RuleDescriptor> ruleDescriptor) {

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("|规则编码|规则描述|是否有条件执行|");
        stringBuilder.append(SystemUtils.LINE_SEPARATOR);
        stringBuilder.append("|-|-|-|");
        stringBuilder.append(SystemUtils.LINE_SEPARATOR);

        ruleDescriptor.forEach(r -> {

            stringBuilder.append("|");
            stringBuilder.append(r.getRuleKey());
            stringBuilder.append("|");
            stringBuilder.append(r.getRuleDescription());
            stringBuilder.append("|");
            stringBuilder.append(r.isWithConditionRule());
            stringBuilder.append("|");
            stringBuilder.append(SystemUtils.LINE_SEPARATOR);

        });

        return stringBuilder.toString();
    }
}
