package io.pragmatic.ddd.visual.entity;

import java.util.List;

public class EntityActionDescriptor {

    private final String methodName;
    private final String description;
    private final List<String> triggerEvents;

    public EntityActionDescriptor(String methodName, String description, List<String> triggerEvents) {
        this.methodName = methodName;
        this.description = description;
        this.triggerEvents = triggerEvents;
    }

    public String getMethodName() {
        return methodName;
    }

    public String getDescription() {
        return description;
    }

    public List<String> getTriggerEvents() {
        return triggerEvents;
    }
}
