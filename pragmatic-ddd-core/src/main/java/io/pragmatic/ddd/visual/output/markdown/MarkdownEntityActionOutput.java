package io.pragmatic.ddd.visual.output.markdown;

import io.pragmatic.ddd.visual.entity.EntityDescriptor;
import io.pragmatic.ddd.visual.entity.IEntityActionOutput;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;

import java.util.List;

public class MarkdownEntityActionOutput implements IEntityActionOutput {
    @Override
    public String output(EntityDescriptor entityDescriptor) {

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("|行为|描述|触发事件|");
        stringBuilder.append(SystemUtils.LINE_SEPARATOR);
        stringBuilder.append("|-|-|-|");
        stringBuilder.append(SystemUtils.LINE_SEPARATOR);

        entityDescriptor.getEntityActionDescriptorList().forEach(s -> {

            stringBuilder.append("|");
            stringBuilder.append(entityDescriptor.getClsName() + "#" + s.getMethodName() + "()");
            stringBuilder.append("|");
            stringBuilder.append(StringUtils.defaultIfBlank(s.getDescription(), " "));
            stringBuilder.append("|");
            stringBuilder.append(this.buildEventInfo(s.getTriggerEvents()));
            stringBuilder.append("|");
            stringBuilder.append(SystemUtils.LINE_SEPARATOR);
        });


        return stringBuilder.toString();
    }

    private String buildEventInfo(List<String> eventDescriptorList) {
        return StringUtils.defaultIfBlank(String.join("</br>", eventDescriptorList), " ");
    }
}
