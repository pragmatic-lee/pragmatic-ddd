package io.pragmatic.ddd.visual.entity;

import java.util.List;

public interface IEntityVisualOutput {
    String output(List<EntityDescriptor> entityDescriptorList);
}
