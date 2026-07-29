package io.pragmatic.ddd.base;

import io.pragmatic.ddd.base.fixture.SampleAggregate;
import io.pragmatic.ddd.base.fixture.SampleEvent;
import io.pragmatic.ddd.base.fixture.SampleMessages;
import io.pragmatic.ddd.event.IDomainEvent;
import io.pragmatic.ddd.operation.EntityOperation;
import io.pragmatic.ddd.operation.OperationException;
import io.pragmatic.ddd.operation.SampleRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 对应设计文档阶段 6.9：AggregateRoot 三种 collectEvent 路径与因果归属测试。
 * 改造自原 EntityOperationCodeTest T1~T8，统一 JUnit5 + 行为命名。
 */
class AggregateRootEventTest {

    @Test
    void defaultAttribution_recordA_collect_operationCodeA() {
        SampleAggregate entity = new SampleAggregate(SampleMessages.INSTANCE, new SampleRegistry());
        entity.recordOperation(SampleRegistry.A);
        SampleEvent e = new SampleEvent("1");
        entity.collectEvent(e);
        assertThat(e.operationCode).isEqualTo("A");
        assertThat(e.version).isNotEqualTo(0);
    }

    @Test
    void multiValueCollection_recordAB_collect_attributionB_andHasAll() {
        SampleAggregate entity = new SampleAggregate(SampleMessages.INSTANCE, new SampleRegistry());
        entity.recordOperation(SampleRegistry.A);
        entity.recordOperation(SampleRegistry.B);
        SampleEvent e = new SampleEvent("1");
        entity.collectEvent(e);
        assertThat(e.operationCode).isEqualTo("B");
        assertThat(entity.hasAllOperations(SampleRegistry.A, SampleRegistry.B)).isTrue();
    }

    @Test
    void explicitPriority_recordA_collectExplicitC_attributionC() {
        SampleAggregate entity = new SampleAggregate(SampleMessages.INSTANCE, new SampleRegistry());
        entity.recordOperation(SampleRegistry.A);
        SampleEvent e = new SampleEvent("1");
        entity.collectEvent(e, SampleRegistry.C);
        assertThat(e.operationCode).isEqualTo("C");
    }

    @Test
    void failFast_enabledButNoRecord_throws() {
        SampleAggregate entity = new SampleAggregate(SampleMessages.INSTANCE, new SampleRegistry());
        SampleEvent e = new SampleEvent("1");
        assertThatThrownBy(() -> entity.collectEvent(e)).isInstanceOf(OperationException.class);
    }

    @Test
    void lenient_noOperationRegistry_collect_operationCodeNull() {
        SampleAggregate entity = new SampleAggregate(SampleMessages.INSTANCE, null);
        SampleEvent e = new SampleEvent("1");
        entity.collectEvent(e);
        assertThat(e.operationCode).isNull();
    }

    @Test
    void delayedEvent_attributionCapturedAtPublish() {
        SampleAggregate entity = new SampleAggregate(SampleMessages.INSTANCE, new SampleRegistry());
        entity.recordOperation(SampleRegistry.A);
        Supplier<IDomainEvent> supplier = () -> new SampleEvent("1");
        entity.collectEvent(supplier);
        entity.recordOperation(SampleRegistry.B);
        List<IDomainEvent> events = entity.getDomainEvents();
        assertThat(events).hasSize(1);
        SampleEvent materialized = (SampleEvent) events.get(0);
        assertThat(materialized.operationCode).isEqualTo("A");
        assertThat(materialized.version).isNotEqualTo(0);
    }

    @Test
    void delayedEvent_materializeIdempotent() {
        SampleAggregate entity = new SampleAggregate(SampleMessages.INSTANCE, new SampleRegistry());
        entity.recordOperation(SampleRegistry.A);
        Supplier<IDomainEvent> supplier = () -> new SampleEvent("1");
        entity.collectEvent(supplier);
        List<IDomainEvent> first = entity.getDomainEvents();
        List<IDomainEvent> second = entity.getDomainEvents();
        assertThat(first.get(0)).isSameAs(second.get(0));
    }

    @Test
    void getDomainEvents_immutable() {
        SampleAggregate entity = new SampleAggregate(SampleMessages.INSTANCE, new SampleRegistry());
        entity.recordOperation(SampleRegistry.A);
        entity.collectEvent(new SampleEvent("1"));
        assertThatThrownBy(() -> entity.getDomainEvents().add(new SampleEvent("2")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void explicitPath_noRecord_required() {
        SampleAggregate entity = new SampleAggregate(SampleMessages.INSTANCE, new SampleRegistry());
        SampleEvent e = new SampleEvent("1");
        entity.collectEvent(e, SampleRegistry.C);
        assertThat(e.operationCode).isEqualTo("C");
    }
}
