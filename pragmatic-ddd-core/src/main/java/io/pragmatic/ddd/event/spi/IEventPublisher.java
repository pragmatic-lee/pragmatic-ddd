package io.pragmatic.ddd.event.spi;

import io.pragmatic.ddd.event.IDomainEvent;

import java.util.List;

/**
 * 领域事件发布端口。
 *
 * @author wizard-lee
 */
public interface IEventPublisher {

    /**
     * 全量发布：触发某事件的所有根订阅者。
     *
     * @param <T>   领域事件类型
     * @param event 待发布的领域事件
     */
    <T extends IDomainEvent> void publish(T event);

    /**
     * 发布给指定订阅者。
     *
     * @param <T>        领域事件类型
     * @param event      待发布的领域事件
     * @param subscriber 目标订阅者别名
     */
    <T extends IDomainEvent> void publish(T event, String subscriber);

    /**
     * 发布给指定订阅者；onlyThis 为 true 时不再向后传播依赖订阅者。
     *
     * @param <T>        领域事件类型
     * @param event      待发布的领域事件
     * @param subscriber 目标订阅者别名
     * @param onlyThis   为 true 时仅投递该订阅者，不再向后传播依赖订阅者
     */
    <T extends IDomainEvent> void publish(T event, String subscriber, boolean onlyThis);

    /**
     * 批量发布一组事件。
     *
     * @param <T>    领域事件类型
     * @param events 待发布的事件列表
     */
    <T extends IDomainEvent> void publishList(List<T> events);
}
