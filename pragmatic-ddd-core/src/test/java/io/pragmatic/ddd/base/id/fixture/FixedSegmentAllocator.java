package io.pragmatic.ddd.base.id.fixture;

import io.pragmatic.ddd.base.id.IdSegment;
import io.pragmatic.ddd.base.id.IIdSegmentAllocator;

/**
 * 固定号段分配器测试夹具：按固定步长依次分配递增的连续号段，并记录分配次数，
 * 便于断言号段生成器"首次惰性拉取 / 耗尽后重新申请"的行为。
 *
 * @author wizard-lee
 */
public class FixedSegmentAllocator implements IIdSegmentAllocator {

    private final long step;
    private long nextSegmentStart;
    private int allocateCount = 0;

    public FixedSegmentAllocator(long initialStart, long step) {
        this.nextSegmentStart = initialStart;
        this.step = step;
    }

    @Override
    public IdSegment allocateNext(String bizKey) {
        allocateCount++;
        long max = nextSegmentStart + step - 1;
        IdSegment segment = new IdSegment(nextSegmentStart, max, step);
        nextSegmentStart = max + 1;
        return segment;
    }

    /** 返回累计申请号段的次数。 */
    public int allocateCount() {
        return allocateCount;
    }
}
