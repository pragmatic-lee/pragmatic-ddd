package io.pragmatic.ddd.event.internal.defaults;

import io.pragmatic.ddd.config.IConfigurationSource;
import io.pragmatic.ddd.event.IDomainEvent;
import io.pragmatic.ddd.event.spi.ITopicResolver;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 三层次可配置 Topic 解析器。
 * 按优先级从高到低匹配：订阅者级别 → 事件级别 → 全局默认。
 *
 * @author wizard-lee
 */
public class ConfigurableTopicResolver implements ITopicResolver {

    private static final String PREFIX = "event.topic.";
    private static final String DEFAULT_KEY = PREFIX + "default";
    private static final String EVENT_KEY = PREFIX + "event.";
    private static final String SUBSCRIBER_KEY = PREFIX + "subscriber.";

    private final String globalDefaultTopic;
    private final Map<String, String> eventTypeTopics;
    private final Map<String, String> subscriberTopics;

    private ConfigurableTopicResolver(String globalDefaultTopic,
                                      Map<String, String> eventTypeTopics,
                                      Map<String, String> subscriberTopics) {
        this.globalDefaultTopic = globalDefaultTopic;
        this.eventTypeTopics = eventTypeTopics;
        this.subscriberTopics = subscriberTopics;
    }

    /**
     * 从配置源按 {@code event.topic} 前缀批量加载 Topic 映射（兼容并收敛既有配置）。
     * 键约定：
     * <ul>
     *   <li>{@code event.topic.default} → 全局默认</li>
     *   <li>{@code event.topic.event.{ClassName}} → 事件级</li>
     *   <li>{@code event.topic.subscriber.{ClassName}#{subscriber}} → 订阅者级</li>
     * </ul>
     *
     * @param source 配置源
     * @return 构建后的解析器
     */
    public static ConfigurableTopicResolver fromSource(IConfigurationSource source) {
        String globalDefault = source.getString(DEFAULT_KEY, null);
        Map<String, String> eventTypeTopics = new HashMap<>();
        Map<String, String> subscriberTopics = new HashMap<>();
        for (String key : source.keys()) {
            if (key.startsWith(EVENT_KEY)) {
                eventTypeTopics.put(key.substring(EVENT_KEY.length()), source.getString(key, ""));
            } else if (key.startsWith(SUBSCRIBER_KEY)) {
                subscriberTopics.put(key.substring(SUBSCRIBER_KEY.length()), source.getString(key, ""));
            }
        }
        return new ConfigurableTopicResolver(globalDefault, eventTypeTopics, subscriberTopics);
    }

    @Override
    public Set<String> getAllTopics() {
        Set<String> topics = new HashSet<>();
        if (globalDefaultTopic != null) {
            topics.add(globalDefaultTopic);
        }
        topics.addAll(eventTypeTopics.values());
        topics.addAll(subscriberTopics.values());
        return topics;
    }

    @Override
    public <T extends IDomainEvent> String resolveForType(Class<T> eventType) {
        String className = eventType.getSimpleName();
        if (eventTypeTopics.containsKey(className)) {
            return eventTypeTopics.get(className);
        }
        return globalDefaultTopic;
    }

    @Override
    public <T extends IDomainEvent> String resolveForEvent(T event, String subscriber) {
        String className = event.getClass().getSimpleName();

        // Level 3: 订阅者级别（最高优先级）
        String subKey = className + "#" + subscriber;
        if (subscriberTopics.containsKey(subKey)) {
            return subscriberTopics.get(subKey);
        }

        // Level 2: 事件级别
        if (eventTypeTopics.containsKey(className)) {
            return eventTypeTopics.get(className);
        }

        // Level 1: 全局默认
        return globalDefaultTopic;
    }

    // ---- Builder ----

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String globalDefaultTopic;
        private final Map<String, String> eventTypeTopics = new HashMap<>();
        private final Map<String, String> subscriberTopics = new HashMap<>();

        public Builder globalDefaultTopic(String topic) {
            this.globalDefaultTopic = topic;
            return this;
        }

        /**
         * @param className 事件类名，如 "OrderPaidEvent"
         * @param topic     topic 名称
         */
        public Builder eventTopic(String className, String topic) {
            this.eventTypeTopics.put(className, topic);
            return this;
        }

        /**
         * @param className  事件类名
         * @param subscriber 订阅者名称
         * @param topic      topic 名称
         */
        public Builder subscriberTopic(String className, String subscriber, String topic) {
            this.subscriberTopics.put(className + "#" + subscriber, topic);
            return this;
        }

        public ConfigurableTopicResolver build() {
            return new ConfigurableTopicResolver(
                    globalDefaultTopic,
                    Map.copyOf(eventTypeTopics),
                    Map.copyOf(subscriberTopics));
        }
    }
}
