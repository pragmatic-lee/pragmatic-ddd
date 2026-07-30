package io.pragmatic.ddd.visual.entity;

import java.util.List;

/**
 * 实体行为描述符 —— 承载实体方法/构造器的名称、描述与触发的事件列表。
 *
 * @author wizard-lee
 */
public class EntityActionDescriptor {

    private final String methodName;
    private final String description;
    private final List<String> triggerEvents;

    /** 构造实体行为描述符。 */
    public EntityActionDescriptor(String methodName, String description, List<String> triggerEvents) {
        this.methodName = methodName;
        this.description = description;
        this.triggerEvents = triggerEvents;
    }

    /** 返回行为方法名。 */
    public String getMethodName() {
        return methodName;
    }

    /** 返回行为描述。 */
    public String getDescription() {
        return description;
    }

    /** 返回触发的事件列表。 */
    public List<String> getTriggerEvents() {
        return triggerEvents;
    }
}
