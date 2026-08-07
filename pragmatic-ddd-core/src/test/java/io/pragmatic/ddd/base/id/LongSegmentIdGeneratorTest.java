package io.pragmatic.ddd.base.id;

import io.pragmatic.ddd.base.id.fixture.FixedSegmentAllocator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LongSegmentIdGenerator 测试：覆盖 AbstractSegmentIdGenerator 号段核心逻辑——
 * 首次惰性拉取、号段内连续发号、耗尽后自动申请新段、批量生成与业务键。
  * @author wizard-lee
 */
class LongSegmentIdGeneratorTest {

    @Test
    void nextId_firstCall_lazilyAllocatesSegment() {
        FixedSegmentAllocator allocator = new FixedSegmentAllocator(1, 3);
        LongSegmentIdGenerator generator = new LongSegmentIdGenerator("order", allocator);

        // 构造期不申请，首次 nextId 才惰性拉取
        assertThat(allocator.allocateCount()).isZero();
        assertThat(generator.nextId()).isEqualTo(1L);
        assertThat(allocator.allocateCount()).isEqualTo(1);
    }

    @Test
    void nextId_withinSegment_issuesConsecutiveIdsWithoutReallocating() {
        FixedSegmentAllocator allocator = new FixedSegmentAllocator(1, 3);
        LongSegmentIdGenerator generator = new LongSegmentIdGenerator("order", allocator);

        // 号段 [1,3]：三次发号均无需重新申请
        assertThat(generator.nextId()).isEqualTo(1L);
        assertThat(generator.nextId()).isEqualTo(2L);
        assertThat(generator.nextId()).isEqualTo(3L);
        assertThat(allocator.allocateCount()).isEqualTo(1);
    }

    @Test
    void nextId_whenSegmentExhausted_allocatesNextSegment() {
        FixedSegmentAllocator allocator = new FixedSegmentAllocator(1, 3);
        LongSegmentIdGenerator generator = new LongSegmentIdGenerator("order", allocator);

        // 耗尽 [1,3] 后，第 4 次申请新段 [4,6]
        generator.nextId();
        generator.nextId();
        generator.nextId();
        assertThat(generator.nextId()).isEqualTo(4L);
        assertThat(allocator.allocateCount()).isEqualTo(2);
        assertThat(generator.nextId()).isEqualTo(5L);
    }

    @Test
    void nextIds_batchReturnsCountConsecutiveIds() {
        FixedSegmentAllocator allocator = new FixedSegmentAllocator(1, 3);
        LongSegmentIdGenerator generator = new LongSegmentIdGenerator("order", allocator);

        List<Long> ids = generator.nextIds(3);

        assertThat(ids).containsExactly(1L, 2L, 3L);
        assertThat(allocator.allocateCount()).isEqualTo(1);
    }

    @Test
    void nextIds_crossingSegmentBoundary_allocatesNewSegment() {
        FixedSegmentAllocator allocator = new FixedSegmentAllocator(1, 2);
        LongSegmentIdGenerator generator = new LongSegmentIdGenerator("order", allocator);

        List<Long> ids = generator.nextIds(3);

        // 号段 [1,2] 耗尽后自动申请 [3,4]
        assertThat(ids).containsExactly(1L, 2L, 3L);
        assertThat(allocator.allocateCount()).isEqualTo(2);
    }

    @Test
    void bizKey_returnsConfiguredKey() {
        LongSegmentIdGenerator generator =
                new LongSegmentIdGenerator("order", new FixedSegmentAllocator(1, 3));

        assertThat(generator.bizKey()).isEqualTo("order");
    }
}
