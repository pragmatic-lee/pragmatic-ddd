package io.pragmatic.ddd.base.id;

import io.pragmatic.ddd.base.id.fixture.FixedSegmentAllocator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * StringSegmentIdGenerator 测试：验证带前缀 / 格式的 String 标识产出与号段推进。
 */
class StringSegmentIdGeneratorTest {

    @Test
    void nextId_formatsRawIdWithPrefix() {
        FixedSegmentAllocator allocator = new FixedSegmentAllocator(100, 3);
        StringSegmentIdGenerator generator =
                new StringSegmentIdGenerator("refund", allocator, "RFD-%07d");

        assertThat(generator.nextId()).isEqualTo("RFD-0000100");
        assertThat(generator.nextId()).isEqualTo("RFD-0000101");
    }

    @Test
    void nextIds_formatsEachId() {
        FixedSegmentAllocator allocator = new FixedSegmentAllocator(1, 3);
        StringSegmentIdGenerator generator =
                new StringSegmentIdGenerator("order", allocator, "ORD-%08d");

        List<String> ids = generator.nextIds(3);

        assertThat(ids).containsExactly("ORD-00000001", "ORD-00000002", "ORD-00000003");
    }

    @Test
    void nextId_whenSegmentExhausted_formatsNextSegmentFirstId() {
        FixedSegmentAllocator allocator = new FixedSegmentAllocator(1, 2);
        StringSegmentIdGenerator generator =
                new StringSegmentIdGenerator("order", allocator, "ORD-%04d");

        generator.nextId();
        generator.nextId();

        assertThat(generator.nextId()).isEqualTo("ORD-0003");
    }
}
