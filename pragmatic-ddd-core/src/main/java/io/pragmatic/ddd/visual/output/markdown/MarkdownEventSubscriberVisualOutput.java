package io.pragmatic.ddd.visual.output.markdown;

import io.pragmatic.ddd.visual.event.EventDescriptor;
import io.pragmatic.ddd.visual.event.EventSubscriberDescriptor;
import io.pragmatic.ddd.visual.event.IEventSubscriberVisualOutput;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 事件订阅 Markdown 输出 —— 将事件订阅描述符列表渲染为 Mermaid flowchart。
 *
 * @author wizard-lee
 */
public class MarkdownEventSubscriberVisualOutput implements IEventSubscriberVisualOutput {
    private static final String START = "```mermaid" + SystemUtils.LINE_SEPARATOR;
    private static final String END = "```" + SystemUtils.LINE_SEPARATOR;

    /** 将事件订阅描述符列表渲染为 Mermaid flowchart 文本。 */
    @Override
    public String output(List<EventDescriptor> eventDescriptorList) {

        StringBuilder stringBuilder = new StringBuilder();

        eventDescriptorList.forEach(s -> {
            String s1 = this.buildEventSubscriber(s);
            stringBuilder.append(s1);
        });

        return stringBuilder.toString();

    }

    private String buildStyle(EventDescriptor eventDescriptor) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("style ");
        stringBuilder.append(eventDescriptor.getEventName());
        stringBuilder.append(" fill: #FFBCBD, stroke: #333");
        stringBuilder.append(SystemUtils.LINE_SEPARATOR);

        eventDescriptor.getSubscriberDescriptorList().forEach(s -> {

            stringBuilder.append("style ");
            stringBuilder.append(s.getSubscriberKey());
            stringBuilder.append(" fill: #74D1A5, stroke: #333");
            stringBuilder.append(SystemUtils.LINE_SEPARATOR);

        });

        return stringBuilder.toString();
    }

    private String buildEvent(EventDescriptor eventDescriptor){
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(eventDescriptor.getEventName());
        stringBuilder.append("[");
        stringBuilder.append(eventDescriptor.getEventName());
        stringBuilder.append("</br>");
        stringBuilder.append(eventDescriptor.getEventDescription());
        stringBuilder.append("]");

        return stringBuilder.toString();
    }

    private String buildEventSubscriber(EventDescriptor eventDescriptor) {

        StringBuilder eventSubscriberStringBuilder = new StringBuilder();

        eventSubscriberStringBuilder.append(START);

        eventSubscriberStringBuilder.append("flowchart TD");
        eventSubscriberStringBuilder.append(SystemUtils.LINE_SEPARATOR);
        eventSubscriberStringBuilder.append(this.buildEvent(eventDescriptor));
        eventSubscriberStringBuilder.append(SystemUtils.LINE_SEPARATOR);

        eventSubscriberStringBuilder.append(buildEventDependOn(eventDescriptor.getEventName(), eventDescriptor.getSubscriberDescriptorList(), "_root_"));

        eventSubscriberStringBuilder.append(buildStyle(eventDescriptor));
        eventSubscriberStringBuilder.append(END);
        return eventSubscriberStringBuilder.toString();
    }

    private String buildEventDependOn(String name, List<EventSubscriberDescriptor> eventSubscriberDescriptors, String dependOnName) {

        List<EventSubscriberDescriptor> rootEventSubscriber = eventSubscriberDescriptors.stream()
                .filter(s -> s.getDependsOnSubscriber().equals(dependOnName))
                .collect(Collectors.toList());


        StringBuilder stringBuilder = new StringBuilder();
        rootEventSubscriber.forEach(s -> {

            stringBuilder.append(name);
            stringBuilder.append(" --> ");
            stringBuilder.append(s.getSubscriberKey());
            stringBuilder.append("[");
            stringBuilder.append(s.getSubscriberKey());
            stringBuilder.append("</br>");
            stringBuilder.append(s.getSubscriberDescription());
            stringBuilder.append("]");
            stringBuilder.append(SystemUtils.LINE_SEPARATOR);

            String s1 = buildEventDependOn(s.getSubscriberKey(), eventSubscriberDescriptors, s.getSubscriberKey());
            if (StringUtils.isNoneEmpty(s1)) {
                stringBuilder.append(s1);
            }
        });

        return stringBuilder.toString();

    }
}
