package io.pragmatic.ddd.visual.event;

import io.pragmatic.ddd.visual.entity.EntityDescriptor;

import java.util.List;

/**
 * 领域事件可视化输出 —— 将事件描述符列表与所属实体渲染为文本。
 *
 * @author wizard-lee
 */
public interface IEventVisualOutput {
    /** 将事件描述符列表与所属实体渲染为文本。 */
    String output(List<EventDescriptor> eventDescriptorList, EntityDescriptor entityDescriptor);
}
