package io.pragmatic.ddd.base;

import io.pragmatic.ddd.base.fixture.SampleAggregate;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 对应设计文档阶段一：AggregateRoot 外部需求声明（requireExternal / getExternalRequirements / 工作单元清理）。
 *
 * @author wizard-lee
 */
class AggregateRootExternalRequirementTest {

    private static final IExternalRequirement RESERVE_INVENTORY = () -> "reserve-inventory";
    private static final IExternalRequirement CHARGE_PAYMENT = () -> "charge-payment";

    @Test
    void noDeclaration_returnsEmpty() {
        SampleAggregate entity = new SampleAggregate();
        assertThat(entity.getExternalRequirements()).isEmpty();
    }

    @Test
    void declaration_preservesOrder() {
        SampleAggregate entity = new SampleAggregate();
        entity.declareExternal(RESERVE_INVENTORY);
        entity.declareExternal(CHARGE_PAYMENT);
        assertThat(entity.getExternalRequirements())
                .extracting(IExternalRequirement::code)
                .containsExactly("reserve-inventory", "charge-payment");
    }

    @Test
    void getExternalRequirements_immutable() {
        SampleAggregate entity = new SampleAggregate();
        entity.declareExternal(RESERVE_INVENTORY);
        assertThatThrownBy(() -> entity.getExternalRequirements().add(CHARGE_PAYMENT))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void clearWorkUnitState_clearsRequirements() {
        SampleAggregate entity = new SampleAggregate();
        entity.declareExternal(RESERVE_INVENTORY);
        entity.clearWorkUnitState();
        assertThat(entity.getExternalRequirements()).isEmpty();
    }

    @Test
    void repeatedDeclaration_notDeduplicated() {
        // 框架不去重：重复声明由 actionKey 撞键在应用层暴露（落地计划已采纳决策 6）
        SampleAggregate entity = new SampleAggregate();
        entity.declareExternal(RESERVE_INVENTORY);
        entity.declareExternal(RESERVE_INVENTORY);
        assertThat(entity.getExternalRequirements()).hasSize(2);
    }
}
