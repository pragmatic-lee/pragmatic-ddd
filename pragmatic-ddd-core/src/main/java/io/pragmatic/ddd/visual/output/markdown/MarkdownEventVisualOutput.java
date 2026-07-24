package io.pragmatic.ddd.visual.output.markdown;

import io.pragmatic.ddd.visual.entity.EntityActionDescriptor;
import io.pragmatic.ddd.visual.entity.EntityDescriptor;
import io.pragmatic.ddd.visual.event.EventDescriptor;
import io.pragmatic.ddd.visual.event.IEventVisualOutput;
import org.apache.commons.lang3.SystemUtils;

import java.util.List;
import java.util.stream.Collectors;

public class MarkdownEventVisualOutput implements IEventVisualOutput {
    @Override
    public String output(List<EventDescriptor> eventDescriptorList, EntityDescriptor entityDescriptor) {

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("|事件名称|描述|事件来源|");
        stringBuilder.append(SystemUtils.LINE_SEPARATOR);
        stringBuilder.append("|-|-|-|");
        stringBuilder.append(SystemUtils.LINE_SEPARATOR);


        eventDescriptorList.forEach(e -> {


            stringBuilder.append("|");
            stringBuilder.append(e.getEventName());
            stringBuilder.append("|");
            stringBuilder.append(e.getEventDescription());
            stringBuilder.append("|");
            stringBuilder.append(triggerEventsAction(e.getEventName(), entityDescriptor.getClsName(), entityDescriptor.getEntityActionDescriptorList()));
            stringBuilder.append("|");
            stringBuilder.append(SystemUtils.LINE_SEPARATOR);

        });
        return stringBuilder.toString();
    }

    private String triggerEventsAction(String eventName, String entityName, List<EntityActionDescriptor> entityActionDescriptorList) {

        return entityActionDescriptorList.stream()
                .filter(t -> t.getTriggerEvents().contains(eventName))
                .map(t -> entityName + "#" + t.getMethodName() + "()")
                .collect(Collectors.joining("</br>"));

    }
}
