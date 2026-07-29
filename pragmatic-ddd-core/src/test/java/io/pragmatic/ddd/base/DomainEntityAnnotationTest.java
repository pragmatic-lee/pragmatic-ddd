package io.pragmatic.ddd.base;

import org.junit.jupiter.api.Test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 对应设计文档阶段 6.15：@DomainEntity 注解契约测试（供 visual/AI 模块依赖）。
 */
class DomainEntityAnnotationTest {

    @DomainEntity(aggregateRoot = "OrderItem", description = "订单项", boundedContext = "order")
    static class OrderItem {
    }

    @Test
    void retentionAndTarget_runtimeAndType() {
        Retention retention = DomainEntity.class.getAnnotation(Retention.class);
        assertThat(retention).isNotNull();
        assertThat(retention.value()).isEqualTo(RetentionPolicy.RUNTIME);

        Target target = DomainEntity.class.getAnnotation(Target.class);
        assertThat(target).isNotNull();
        assertThat(target.value()).contains(ElementType.TYPE);
    }

    @DomainEntity
    static class EmptyAnnotated {
    }

    @Test
    void defaultValues() {
        DomainEntity annotation = EmptyAnnotated.class.getAnnotation(DomainEntity.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.aggregateRoot()).isEmpty();
        assertThat(annotation.description()).isEmpty();
        assertThat(annotation.boundedContext()).isEmpty();
    }

    @Test
    void reflectableAtRuntime() {
        DomainEntity annotation = OrderItem.class.getAnnotation(DomainEntity.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.aggregateRoot()).isEqualTo("OrderItem");
        assertThat(annotation.description()).isEqualTo("订单项");
        assertThat(annotation.boundedContext()).isEqualTo("order");
    }
}
