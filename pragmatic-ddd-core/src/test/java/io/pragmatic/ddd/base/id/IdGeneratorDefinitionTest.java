package io.pragmatic.ddd.base.id;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IdGeneratorDefinition 生成器定义测试：验证默认值、全参构造与 getter/setter 读写。
 */
class IdGeneratorDefinitionTest {

    @Test
    void noArgConstructor_hasSensibleDefaults() {
        IdGeneratorDefinition def = new IdGeneratorDefinition();

        assertThat(def.getStartId()).isEqualTo(1);
        assertThat(def.getStep()).isEqualTo(1000);
        assertThat(def.getIdType()).isEqualTo(IdType.LONG);
        assertThat(def.getBizKey()).isNull();
        assertThat(def.getFormat()).isNull();
        assertThat(def.getDescription()).isNull();
    }

    @Test
    void allArgConstructor_populatesFields() {
        IdGeneratorDefinition def = new IdGeneratorDefinition(
                "order", 100, 500, IdType.STRING, "ORD-%08d", "订单渠道");

        assertThat(def.getBizKey()).isEqualTo("order");
        assertThat(def.getStartId()).isEqualTo(100);
        assertThat(def.getStep()).isEqualTo(500);
        assertThat(def.getIdType()).isEqualTo(IdType.STRING);
        assertThat(def.getFormat()).isEqualTo("ORD-%08d");
        assertThat(def.getDescription()).isEqualTo("订单渠道");
    }

    @Test
    void setters_updateFields() {
        IdGeneratorDefinition def = new IdGeneratorDefinition();
        def.setBizKey("payment");
        def.setStartId(999);
        def.setStep(50);
        def.setIdType(IdType.STRING);
        def.setFormat("PAY-%06d");
        def.setDescription("支付渠道");

        assertThat(def.getBizKey()).isEqualTo("payment");
        assertThat(def.getStartId()).isEqualTo(999);
        assertThat(def.getStep()).isEqualTo(50);
        assertThat(def.getIdType()).isEqualTo(IdType.STRING);
        assertThat(def.getFormat()).isEqualTo("PAY-%06d");
        assertThat(def.getDescription()).isEqualTo("支付渠道");
    }
}
