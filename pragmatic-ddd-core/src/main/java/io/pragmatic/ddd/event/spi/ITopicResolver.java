package io.pragmatic.ddd.event.spi;

import io.pragmatic.ddd.event.IDomainEvent;

import java.util.Set;

/**
 * Topic 解析器。
 *
 * <p>承担两个职责：
 * <ol>
 *   <li>获取全量 Topic 列表——用于初始化 MQ Producer/Consumer 实例</li>
 *   <li>解析目标 Topic——根据事件或订阅者信息确定消息应发送的目标 topic</li>
 * </ol>
 *
 * <p>所有映射关系在应用启动前完成静态配置。
 * {@link #getAllTopics()} 保证返回准确、完整的全量 topic 列表。
 */
public interface ITopicResolver {

    /**
     * 获取全量 Topic 列表。
     * 基于静态配置原则，保证返回所有已配置的 topic。
     * 用于应用启动时批量创建 MQ Producer/Consumer。
     */
    Set<String> getAllTopics();

    /**
     * 为事件类型解析 topic。
     *
     * @param eventType 事件类型
     * @return topic 名称
     */
    <T extends IDomainEvent> String resolveForType(Class<T> eventType);

    /**
     * 为事件实例和订阅者解析 topic。
     *
     * <p>默认委托给 resolveForType()，即所有订阅者共享同一 topic。
     * 子类可重写实现按订阅者路由。
     *
     * @param event      事件实例
     * @param subscriber 订阅者名称
     * @return topic 名称
     */
    default <T extends IDomainEvent> String resolveForEvent(T event, String subscriber) {
        return resolveForType((Class<T>) event.getClass());
    }
}
