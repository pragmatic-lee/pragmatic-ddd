package io.pragmatic.ddd.rocketmq;

import io.pragmatic.ddd.event.internal.defaults.ConfigurableTopicResolver;
import io.pragmatic.ddd.event.internal.defaults.SubscriberOrderManager;
import io.pragmatic.ddd.event.spi.ExecuteStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link LocalStubEventManager} 纯单元测试（不依赖真实 RocketMQ），
 * 验证发布时构建的 SubscribeData、发往的 topic 以及顺序链触发逻辑。
 *
 * @author wizard-lee
 */
class LocalStubEventManagerTest {

    private LocalStubEventManager manager() {
        return new LocalStubEventManager(
                new SubscriberOrderManager(),
                new Fastjson2EventSerializer(),
                ConfigurableTopicResolver.builder().globalDefaultTopic("pdd_ddd_default_topic").build());
    }

    @Test
    void publish_allSubscribers_sendsToOneTopicPerSubscriber() {
        LocalStubEventManager manager = manager();
        manager.registerSubscriber("subA", MyDomainEvent.class, e -> { });
        manager.registerSubscriber("subB", MyDomainEvent.class, e -> { });

        manager.publish(MyDomainEvent.buildEvent("a", "a"));

        List<LocalStubEventManager.SentRecord> sent = manager.sent();
        assertThat(sent).hasSize(2);
        assertThat(sent).allMatch(r -> "pdd_ddd_default_topic".equals(r.topic()));
        assertThat(sent.stream().map(r -> r.subscribeData().getName())).containsExactlyInAnyOrder("subA", "subB");
    }

    @Test
    void publish_withCondition_skipsSkippedSubscribers() {
        LocalStubEventManager manager = manager();
        manager.registerSubscriber("skipMe", MyDomainEvent.class, e -> { },
                evt -> evt.getName().equals("match") ? ExecuteStatus.EXECUTE : ExecuteStatus.SKIP);
        manager.registerSubscriber("keepMe", MyDomainEvent.class, e -> { });

        manager.publish(MyDomainEvent.buildEvent("other", "other"));

        List<LocalStubEventManager.SentRecord> sent = manager.sent();
        assertThat(sent).hasSize(1);
        assertThat(sent.get(0).subscribeData().getName()).isEqualTo("keepMe");
    }

    @Test
    void publish_onlyThis_true_doesNotTriggerDependentSubscribers() {
        LocalStubEventManager manager = manager();
        manager.registerSubscriber("root", MyDomainEvent.class, e -> { }, null, "leaf");
        manager.registerSubscriber("leaf", MyDomainEvent.class, e -> { });

        manager.publish(MyDomainEvent.buildEvent("a", "a"), "root", true);

        List<LocalStubEventManager.SentRecord> sent = manager.sent();
        assertThat(sent).hasSize(1);
        assertThat(sent.get(0).subscribeData().getName()).isEqualTo("root");
    }

    @Test
    void publish_onlyThis_false_triggersDependentChain() {
        LocalStubEventManager manager = manager();
        manager.registerSubscriber("root", MyDomainEvent.class, e -> { }, null, "leaf");
        manager.registerSubscriber("leaf", MyDomainEvent.class, e -> { });

        manager.publish(MyDomainEvent.buildEvent("a", "a"), "root", false);

        List<LocalStubEventManager.SentRecord> sent = manager.sent();
        assertThat(sent.stream().map(r -> r.subscribeData().getName()))
                .containsExactlyInAnyOrder("root", "leaf");
    }

    @Test
    void publish_unknownEvent_isIgnored() {
        LocalStubEventManager manager = manager();

        manager.publish(MyDomainEvent.buildEvent("a", "a"));

        assertThat(manager.sent()).isEmpty();
    }
}
