package io.pragmatic.ddd.visual.output.markdown;

import io.pragmatic.ddd.visual.DomainModelVisualInfo;
import io.pragmatic.ddd.visual.IFullViewVisualOutput;
import io.pragmatic.ddd.visual.entity.EntityDescriptor;
import io.pragmatic.ddd.visual.event.EventDescriptor;
import org.apache.commons.lang3.SystemUtils;

import java.util.List;
import java.util.Optional;

public class MarkdownFullViewVisualOutput implements IFullViewVisualOutput {
    @Override
    public String output(DomainModelVisualInfo domainModelVisualInfo) {

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("```mermaid");
        stringBuilder.append(SystemUtils.LINE_SEPARATOR);
        stringBuilder.append("flowchart LR");
        stringBuilder.append(SystemUtils.LINE_SEPARATOR);


        Optional<EntityDescriptor> entityDescriptor = domainModelVisualInfo.getEntityDescriptorList().stream()
                .filter(EntityDescriptor::getRoot)
                .findFirst();


        entityDescriptor.ifPresent(e -> {
            String entitySubgraph = buildEntitySubgraph(e);
            stringBuilder.append(entitySubgraph);

            String actionSubgraph = buildActionSubgraph(e);
            stringBuilder.append(actionSubgraph);

        });

        String eventSubgraph = buildEventSubgraph(domainModelVisualInfo.getEventDescriptors());

        stringBuilder.append(eventSubgraph);

        String eventSubscriberSubgraph = buildEventSubscriberSubgraph(domainModelVisualInfo.getEventDescriptors());
        stringBuilder.append(eventSubscriberSubgraph);

        entityDescriptor.ifPresent(e -> {
            String actionEntityRelation = buildActionEntityRelation(e);
            stringBuilder.append(actionEntityRelation);

            String eventActionRelation = buildEventActionRelation(e);
            stringBuilder.append(eventActionRelation);
        });

        String eventSubscriberEventRelation = buildEventSubscriberEventRelation(domainModelVisualInfo.getEventDescriptors());
        stringBuilder.append(eventSubscriberEventRelation);


        stringBuilder.append("```");

        return stringBuilder.toString();
    }

    private String buildEventSubscriberEventRelation(List<EventDescriptor> eventDescriptorList) {
        StringBuilder stringBuilder = new StringBuilder();

        eventDescriptorList.forEach(evt ->
                evt.getSubscriberDescriptorList().forEach(sub -> {

                    stringBuilder.append(evt.getEventName());
                    stringBuilder.append("-.->");
                    stringBuilder.append(evt.getEventName());
                    stringBuilder.append("_");
                    stringBuilder.append(sub.getSubscriberKey());
                    stringBuilder.append(SystemUtils.LINE_SEPARATOR);
                }));
        return stringBuilder.toString();
    }


    private String buildEventActionRelation(EntityDescriptor entityDescriptor) {
        StringBuilder stringBuilder = new StringBuilder();

        entityDescriptor.getEntityActionDescriptorList().forEach(action -> {
            action.getTriggerEvents().forEach(evt -> {

                String keyName = action.getMethodName();
                if (action.getMethodName().equals(entityDescriptor.getClsName())) {
                    keyName = "constructor_";
                }

                stringBuilder.append(keyName);
                stringBuilder.append("-.->");
                stringBuilder.append(evt);
                stringBuilder.append(SystemUtils.LINE_SEPARATOR);
            });
        });

        stringBuilder.append(SystemUtils.LINE_SEPARATOR);
        return stringBuilder.toString();
    }

    private String buildActionEntityRelation(EntityDescriptor entityDescriptor) {
        StringBuilder stringBuilder = new StringBuilder();

        entityDescriptor.getEntityActionDescriptorList().forEach(action -> {

            String keyName = action.getMethodName();
            if (action.getMethodName().equals(entityDescriptor.getClsName())) {
                keyName = "constructor_";
            }

            stringBuilder.append(entityDescriptor.getClsName());
            stringBuilder.append("-.->");
            stringBuilder.append(keyName);
            stringBuilder.append(SystemUtils.LINE_SEPARATOR);
        });

        stringBuilder.append(SystemUtils.LINE_SEPARATOR);
        return stringBuilder.toString();

    }

    private String buildEventSubgraph(List<EventDescriptor> eventDescriptorList) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("subgraph 事件");
        stringBuilder.append(SystemUtils.LINE_SEPARATOR);


        eventDescriptorList.forEach(evt -> {

            stringBuilder.append(evt.getEventName());
            stringBuilder.append("[");
            stringBuilder.append(evt.getEventName());
            stringBuilder.append("</br>");
            stringBuilder.append(evt.getEventDescription());
            stringBuilder.append("]");
            stringBuilder.append(SystemUtils.LINE_SEPARATOR);

        });


        stringBuilder.append(SystemUtils.LINE_SEPARATOR);
        stringBuilder.append("end");
        stringBuilder.append(SystemUtils.LINE_SEPARATOR);

        return stringBuilder.toString();
    }

    private String buildActionSubgraph(EntityDescriptor entityDescriptor) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("subgraph 操作");
        stringBuilder.append(SystemUtils.LINE_SEPARATOR);


        entityDescriptor.getEntityActionDescriptorList().forEach(a -> {

            String keyName = a.getMethodName();
            if (a.getMethodName().equals(entityDescriptor.getClsName())) {
                keyName = "constructor_";
            }

            stringBuilder.append(keyName);
            stringBuilder.append("[\"");
            stringBuilder.append(a.getMethodName());
            stringBuilder.append("()");
            stringBuilder.append("</br>");
            stringBuilder.append(a.getDescription());
            stringBuilder.append("\"]");
            stringBuilder.append(SystemUtils.LINE_SEPARATOR);
        });
        stringBuilder.append(SystemUtils.LINE_SEPARATOR);
        stringBuilder.append("end");
        stringBuilder.append(SystemUtils.LINE_SEPARATOR);

        return stringBuilder.toString();
    }


    private String buildEntitySubgraph(EntityDescriptor entityDescriptor) {

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("subgraph 实体");
        stringBuilder.append(SystemUtils.LINE_SEPARATOR);
        stringBuilder.append(entityDescriptor.getClsName());
        stringBuilder.append("[");
        stringBuilder.append(entityDescriptor.getClsName());
        stringBuilder.append("</br>");
        stringBuilder.append(entityDescriptor.getDescription());
        stringBuilder.append("]");
        stringBuilder.append(SystemUtils.LINE_SEPARATOR);
        stringBuilder.append("end");
        stringBuilder.append(SystemUtils.LINE_SEPARATOR);

        return stringBuilder.toString();
    }

    private String buildEventSubscriberSubgraph(List<EventDescriptor> eventDescriptorList) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("subgraph 事件订阅");
        stringBuilder.append(SystemUtils.LINE_SEPARATOR);

        eventDescriptorList.forEach(evt -> evt.getSubscriberDescriptorList().forEach(sub ->
                        stringBuilder
                                .append(evt.getEventName())
                                .append("_")
                                .append(sub.getSubscriberKey())
                                .append("[")
                                .append(sub.getSubscriberKey())
                                .append("</br>")
                                .append(sub.getSubscriberDescription())
                                .append("]")
                                .append(SystemUtils.LINE_SEPARATOR)
                )
        );


        stringBuilder.append(SystemUtils.LINE_SEPARATOR);
        stringBuilder.append("end");
        stringBuilder.append(SystemUtils.LINE_SEPARATOR);

        return stringBuilder.toString();
    }
}

