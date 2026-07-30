package io.pragmatic.ddd.visual.event;

import io.pragmatic.ddd.base.AbstractSubscriberKey;
import io.pragmatic.ddd.base.AbstractEntity;
import io.pragmatic.ddd.event.spi.IEventManager;
import io.pragmatic.ddd.event.spi.ISubscriberOrderManager;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 领域事件解析器 —— 借助事件管理器和查找器，提取事件及其订阅依赖并构建描述符。
 *
 * @author wizard-lee
 */
public class EventParser {

    private final IEventManager eventManager;
    private final Map<Class<?>, IEventFinder> eventFinderMap = new HashMap<>();

    /** 构造解析器并持有事件管理器。 */
    public EventParser(IEventManager eventManager) {
        this.eventManager = eventManager;
    }

    /** 注册某实体类的领域事件查找器。 */
    public <T extends AbstractEntity<?>> void registerDomainEvent(Class<T> entityClass,
                                                                  IEventFinder finder) {
        eventFinderMap.put(entityClass, finder);
    }

    /** 解析实体类的全部领域事件，返回事件描述符列表。 */
    public <T extends AbstractEntity<?>> List<EventDescriptor> parse(Class<T> cls) {
        IEventFinder iEventFinder = this.eventFinderMap.get(cls);
        if (iEventFinder == null) {
            return Collections.emptyList();
        }

        List<Class<?>> baseDomainEvents = Optional.ofNullable(iEventFinder.findersList(cls))
                .orElse(Collections.emptyList());

        return baseDomainEvents
                .stream().map(evt -> {
                    DomainEventVisual domainEventDescriptor = evt.getAnnotation(DomainEventVisual.class);
                    String eventName = evt.getSimpleName();

                    List<ISubscriberOrderManager.OrderEdge> eventSubscriberInfoList =
                            eventManager.findEventDependencies(eventName);

                    List<EventSubscriberDescriptor> subscriberDescriptorList = eventSubscriberInfoList.stream()
                            .map(s -> {
                                ISubscriberOrderManager.OrderEdge parent = this.findParent(eventSubscriberInfoList,
                                        s.successor());

                                String dependOn = Optional.ofNullable(parent)
                                        .map(t -> t.predecessor())
                                        .orElse("");

                                return new EventSubscriberDescriptor(s.successor(),
                                        Optional.ofNullable(iEventFinder.eventSubscribeKey().getKeyInfo(s.successor()))
                                                .map(AbstractSubscriberKey.KeySetting::getDescription).orElse(""),
                                        dependOn);
                            }).collect(Collectors.toList());

                    return new EventDescriptor(eventName,
                            Optional.ofNullable(domainEventDescriptor)
                                    .map(DomainEventVisual::description)
                                    .orElse(eventName),
                            subscriberDescriptorList);
                }).collect(Collectors.toList());
    }

    private ISubscriberOrderManager.OrderEdge findParent(List<ISubscriberOrderManager.OrderEdge> edgeList,
                                                        String childKey) {
        return edgeList.stream().filter(s -> s.successor().equals(childKey))
                .findFirst()
                .orElse(null);
    }
}
