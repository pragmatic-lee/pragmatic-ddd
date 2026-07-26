package io.pragmatic.ddd.base.test2;

import io.pragmatic.ddd.base.AbstractEntity;
import io.pragmatic.ddd.base.BrokenRuleMessage;
import io.pragmatic.ddd.event.BaseDomainEvent;
import io.pragmatic.ddd.event.IDomainEvent;
import io.pragmatic.ddd.operation.EntityOperation;
import io.pragmatic.ddd.operation.OperationException;
import io.pragmatic.ddd.operation.OperationRegistry;
import io.pragmatic.ddd.operation.SampleRegistry;
import org.junit.Test;

import java.util.List;
import java.util.function.Supplier;

/**
 * 对应重构计划第 5.1 节测试计划：验证 operationCode 因果对齐与 fail-fast 行为。
 */
public class EntityOperationCodeTest {

    // ===================== 测试夹具 =====================

    /** 启用 operation 体系的实体 */
    public static final class SampleOpEntity extends AbstractEntity<Long> {
        private final OperationRegistry registry;

        public SampleOpEntity(OperationRegistry registry) {
            this.registry = registry;
        }

        @Override
        protected BrokenRuleMessage getBrokenRuleMessages() {
            return BrokenRuleMessage.of();
        }

        @Override
        protected OperationRegistry entityOperations() {
            return registry;
        }

        public void doRecord(EntityOperation op) {
            this.recordOperation(op);
        }

        public SampleEvent doPublish() {
            SampleEvent e = new SampleEvent("1");
            this.publishEvent(e);
            return e;
        }

        public SampleEvent doPublishExplicit(EntityOperation op) {
            SampleEvent e = new SampleEvent("1");
            this.publishEvent(e, op);
            return e;
        }

        public void doPublishDelayed(Supplier<IDomainEvent> supplier) {
            this.publishEvent(supplier);
        }
    }

    /** 未启用 operation 体系的实体（entityOperations() 返回 null） */
    public static final class NoOpEntity extends AbstractEntity<Long> {
        @Override
        protected BrokenRuleMessage getBrokenRuleMessages() {
            return BrokenRuleMessage.of();
        }

        @Override
        protected OperationRegistry entityOperations() {
            return null;
        }

        public void doRecord(EntityOperation op) {
            this.recordOperation(op);
        }

        public SampleEvent doPublish() {
            SampleEvent e = new SampleEvent("1");
            this.publishEvent(e);
            return e;
        }
    }

    public static final class SampleEvent extends BaseDomainEvent {
        public SampleEvent(String entityId) {
            super(entityId);
        }

        protected SampleEvent() {
            super();
        }
    }

    // ===================== 用例 =====================

    /** T1 默认归属：record(A) → publishEvent(e) → operationCode == "A" */
    @Test
    public void testDefaultAttribution() {
        SampleOpEntity entity = new SampleOpEntity(new SampleRegistry());
        entity.doRecord(SampleRegistry.A);
        SampleEvent e = entity.doPublish();
        assert "A".equals(e.operationCode);
    }

    /** T2 多值收集不受影响：record(A) → record(B) → publishEvent(e) → 归属 B 且 hasAllOperations(A,B) */
    @Test
    public void testMultiValueCollectionUnaffected() {
        SampleOpEntity entity = new SampleOpEntity(new SampleRegistry());
        entity.doRecord(SampleRegistry.A);
        entity.doRecord(SampleRegistry.B);
        SampleEvent e = entity.doPublish();
        assert "B".equals(e.operationCode);
        assert entity.hasAllOperations(SampleRegistry.A, SampleRegistry.B);
    }

    /** T3 显式优先：record(A) → publishEvent(e, C) → operationCode == "C" */
    @Test
    public void testExplicitPriority() {
        SampleOpEntity entity = new SampleOpEntity(new SampleRegistry());
        entity.doRecord(SampleRegistry.A);
        SampleEvent e = entity.doPublishExplicit(SampleRegistry.C);
        assert "C".equals(e.operationCode);
    }

    /** T4 fail-fast：启用 operation 体系却未 record 直接 publish → 抛 OperationException */
    @Test(expected = OperationException.class)
    public void testFailFastWhenNoRecord() {
        SampleOpEntity entity = new SampleOpEntity(new SampleRegistry());
        entity.doPublish();
    }

    /** T5 宽松分支：entityOperations()==null 的实体 publishEvent → operationCode == null，不抛异常 */
    @Test
    public void testLenientBranch() {
        NoOpEntity entity = new NoOpEntity();
        SampleEvent e = entity.doPublish();
        assert e.operationCode == null;
    }

    /** T6 延迟事件归属：record(A) → publish(supplier) → record(B) → getDomainEvents() → 归属 A 且 version 已赋值 */
    @Test
    public void testDelayedEventAttribution() {
        SampleOpEntity entity = new SampleOpEntity(new SampleRegistry());
        entity.doRecord(SampleRegistry.A);
        entity.doPublishDelayed(() -> new SampleEvent("1"));
        entity.doRecord(SampleRegistry.B);

        List<IDomainEvent> events = entity.getDomainEvents();
        assert events.size() == 1;
        SampleEvent materialized = (SampleEvent) events.get(0);
        assert "A".equals(materialized.operationCode);
        assert materialized.version != 0;
    }

    /** T7 P6 防御：entityOperations()==null 的实体调 recordOperation → 抛 OperationException */
    @Test(expected = OperationException.class)
    public void testP6Defense() {
        NoOpEntity entity = new NoOpEntity();
        entity.doRecord(SampleRegistry.A);
    }

    /** T8 P5 清理：record(A) + publish 后 clearWorkUnitState() → 事件/操作/指针全部清空 */
    @Test
    public void testClearWorkUnitState() {
        SampleOpEntity entity = new SampleOpEntity(new SampleRegistry());
        entity.doRecord(SampleRegistry.A);
        entity.doPublish();
        assert !entity.getDomainEvents().isEmpty();
        assert entity.hasOperation(SampleRegistry.A);

        entity.clearWorkUnitState();
        assert entity.getDomainEvents().isEmpty();
        assert !entity.hasOperation(SampleRegistry.A);
        // 指针已重置：再 publish 应再次 fail-fast（验证 lastRecordedOperation 被清空）
        boolean threw = false;
        try {
            entity.doPublish();
        } catch (OperationException ignored) {
            threw = true;
        }
        assert threw;
    }
}
