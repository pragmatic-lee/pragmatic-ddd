package io.pragmatic.ddd.event.spi;

import java.util.List;
import java.util.Map;

/**
 * 事件管理器端口，组合发布、注册与生命周期三类能力。
 *
 * @author wizard-lee
 */
public interface IEventManager extends IEventPublisher, IEventRegistry, IEventLifecycle {

    /** 返回全部事件名及其订阅者别名映射。 */
    Map<String, List<String>> allEvents();

    /** 返回某事件的全部订阅依赖边。 */
    List<ISubscriberOrderManager.OrderEdge> findEventDependencies(String eventName);
}
