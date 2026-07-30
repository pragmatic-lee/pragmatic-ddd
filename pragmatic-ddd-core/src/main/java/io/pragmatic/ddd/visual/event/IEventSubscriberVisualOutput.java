package io.pragmatic.ddd.visual.event;

import java.util.List;

/**
 * 事件订阅者可视化输出 —— 将事件订阅者描述符列表渲染为文本。
 *
 * @author wizard-lee
 */
public interface IEventSubscriberVisualOutput {

    /** 将事件订阅者描述符列表渲染为文本。 */
    String output(List<EventDescriptor> eventDescriptorList);
}
