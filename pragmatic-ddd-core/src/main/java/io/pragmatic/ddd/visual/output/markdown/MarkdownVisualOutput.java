package io.pragmatic.ddd.visual.output.markdown;

import io.pragmatic.ddd.visual.DomainModelVisualInfo;
import io.pragmatic.ddd.visual.IFullViewVisualOutput;
import io.pragmatic.ddd.visual.IVisualOutput;
import io.pragmatic.ddd.visual.application.IApplicationServiceVisualOutput;
import io.pragmatic.ddd.visual.entity.EntityDescriptor;
import io.pragmatic.ddd.visual.entity.IEntityActionOutput;
import io.pragmatic.ddd.visual.entity.IEntityVisualOutput;
import io.pragmatic.ddd.visual.entity.IEnumValueVisualOutput;
import io.pragmatic.ddd.visual.event.IEventSubscriberVisualOutput;
import io.pragmatic.ddd.visual.event.IEventVisualOutput;
import io.pragmatic.ddd.visual.rule.IEntityRuleVisualOutput;
import org.apache.commons.lang3.SystemUtils;

public class MarkdownVisualOutput implements IVisualOutput {

    private final IApplicationServiceVisualOutput applicationServiceVisualOutput;
    private final IEventVisualOutput eventVisualOutput;
    private final IEventSubscriberVisualOutput eventSubscriberVisualOutput;
    private final IEntityRuleVisualOutput entityRuleVisualOutput;
    private final IEnumValueVisualOutput enumValueVisualOutput;
    private final IEntityVisualOutput entityVisualOutput;
    private final IFullViewVisualOutput fullViewVisualOutput;
    private final IEntityActionOutput actionOutput;

    public MarkdownVisualOutput() {

        applicationServiceVisualOutput = new MarkdownApplicationServiceVisualOutput();
        eventVisualOutput = new MarkdownEventVisualOutput();
        eventSubscriberVisualOutput = new MarkdownEventSubscriberVisualOutput();
        entityRuleVisualOutput = new MarkdownEntityRuleVisualOutput();
        entityVisualOutput = new MarkdownEntityVisualOutput();
        fullViewVisualOutput = new MarkdownFullViewVisualOutput();
        actionOutput = new MarkdownEntityActionOutput();
        enumValueVisualOutput = new MarkDownEnumValueVisualOutput();
    }

    @Override
    public String output(DomainModelVisualInfo domainModelVisualInfo) {

        EntityDescriptor first = domainModelVisualInfo.getEntityDescriptorList()
                .stream()
                .filter(EntityDescriptor::getRoot)
                .findFirst()
                .orElse(null);

        StringBuilder stringBuilder = new StringBuilder();

        stringBuilder.append("# 可视化文档");
        stringBuilder.append(SystemUtils.LINE_SEPARATOR);
        stringBuilder.append("## * 实体模型");
        stringBuilder.append(SystemUtils.LINE_SEPARATOR);
        stringBuilder.append(entityVisualOutput.output(domainModelVisualInfo.getEntityDescriptorList()));
        stringBuilder.append(SystemUtils.LINE_SEPARATOR);

        stringBuilder.append("## * 实体枚举");
        stringBuilder.append(SystemUtils.LINE_SEPARATOR);
        stringBuilder.append(enumValueVisualOutput.output(domainModelVisualInfo.getEnumInfoDescriptorList()));
        stringBuilder.append(SystemUtils.LINE_SEPARATOR);

        stringBuilder.append("## * 实体方法");
        stringBuilder.append(SystemUtils.LINE_SEPARATOR);
        stringBuilder.append(actionOutput.output(first));
        stringBuilder.append(SystemUtils.LINE_SEPARATOR);


        stringBuilder.append("## * 实体规则");
        stringBuilder.append(SystemUtils.LINE_SEPARATOR);
        stringBuilder.append(entityRuleVisualOutput.output(domainModelVisualInfo.getRuleDescriptorList()));
        stringBuilder.append(SystemUtils.LINE_SEPARATOR);

        stringBuilder.append("## * 实体事件");
        stringBuilder.append(SystemUtils.LINE_SEPARATOR);
        stringBuilder.append(eventVisualOutput.output(domainModelVisualInfo.getEventDescriptors(), first));

        stringBuilder.append("## * 领域事件订阅");
        stringBuilder.append(SystemUtils.LINE_SEPARATOR);
        stringBuilder.append(eventSubscriberVisualOutput.output(domainModelVisualInfo.getEventDescriptors()));

        stringBuilder.append("## * 领域服务");
        stringBuilder.append(SystemUtils.LINE_SEPARATOR);

        stringBuilder.append("## * 应用服务");
        stringBuilder.append(SystemUtils.LINE_SEPARATOR);

        stringBuilder.append(applicationServiceVisualOutput.output(domainModelVisualInfo.getApplicationDescriptors()));

        stringBuilder.append("## * 全景视图");
        stringBuilder.append(SystemUtils.LINE_SEPARATOR);

        stringBuilder.append(fullViewVisualOutput.output(domainModelVisualInfo));

        return stringBuilder.toString();
    }
}
