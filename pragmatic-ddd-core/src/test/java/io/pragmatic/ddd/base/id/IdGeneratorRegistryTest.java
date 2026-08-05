package io.pragmatic.ddd.base.id;

import io.pragmatic.ddd.base.id.fixture.FixedSegmentAllocator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * IdGeneratorRegistry 多生成器注册中心测试：验证按 bizKey 隔离、按 IdType 构建与取号异常语义。
 */
class IdGeneratorRegistryTest {

    @Test
    void registerDirect_isolatesGeneratorsByBizKey() {
        IdGeneratorRegistry registry = new IdGeneratorRegistry();
        registry.register(new LongSegmentIdGenerator("order", new FixedSegmentAllocator(1, 3)));
        registry.register(new LongSegmentIdGenerator("payment", new FixedSegmentAllocator(100, 3)));

        assertThat(registry.<Long>nextId("order")).isEqualTo(1L);
        assertThat(registry.<Long>nextId("payment")).isEqualTo(100L);
        // 各渠道独立号段，互不干扰
        assertThat(registry.<Long>nextId("order")).isEqualTo(2L);
        assertThat(registry.<Long>nextId("payment")).isEqualTo(101L);
    }

    @Test
    void registerByDefinition_longType_buildsLongGenerator() {
        IdGeneratorRegistry registry = new IdGeneratorRegistry();
        FixedSegmentAllocator allocator = new FixedSegmentAllocator(1, 3);
        IdGeneratorDefinition def = new IdGeneratorDefinition(
                "order", 1, 3, IdType.LONG, null, "订单渠道");

        registry.register(def, allocator);

        assertThat(registry.<Long>nextId("order")).isEqualTo(1L);
    }

    @Test
    void registerByDefinition_stringType_buildsStringGenerator() {
        IdGeneratorRegistry registry = new IdGeneratorRegistry();
        FixedSegmentAllocator allocator = new FixedSegmentAllocator(1, 3);
        IdGeneratorDefinition def = new IdGeneratorDefinition(
                "refund", 1, 3, IdType.STRING, "RFD-%07d", "退款渠道");

        registry.register(def, allocator);

        assertThat(registry.<String>nextId("refund")).isEqualTo("RFD-0000001");
    }

    @Test
    void get_unregisteredBizKey_throwsIllegalArgument() {
        IdGeneratorRegistry registry = new IdGeneratorRegistry();

        assertThatThrownBy(() -> registry.get("missing"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing");
    }

    @Test
    void nextId_unregisteredBizKey_throwsIllegalArgument() {
        IdGeneratorRegistry registry = new IdGeneratorRegistry();

        assertThatThrownBy(() -> registry.<Long>nextId("missing"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void registerSameBizKey_overwritesPrevious() {
        IdGeneratorRegistry registry = new IdGeneratorRegistry();
        registry.register(new LongSegmentIdGenerator("order", new FixedSegmentAllocator(1, 3)));
        registry.register(new LongSegmentIdGenerator("order", new FixedSegmentAllocator(50, 3)));

        // 后注册的覆盖先注册的
        assertThat(registry.<Long>nextId("order")).isEqualTo(50L);
    }
}
