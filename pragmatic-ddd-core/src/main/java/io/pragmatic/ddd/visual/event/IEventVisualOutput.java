package io.pragmatic.ddd.visual.event;

import io.pragmatic.ddd.visual.entity.EntityDescriptor;

import java.util.List;

public interface IEventVisualOutput {
    String output(List<EventDescriptor> eventDescriptorList, EntityDescriptor entityDescriptor);
}
