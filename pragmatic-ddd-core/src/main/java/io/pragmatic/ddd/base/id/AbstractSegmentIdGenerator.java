package io.pragmatic.ddd.base.id;

import java.util.ArrayList;
import java.util.List;

/**
 * 号段模式生成器基类：内存持有当前号段，按需向 IIdSegmentAllocator 申请下一段。
 * 子类只需提供 Long → T 的转换规则（纯数字 / 带前缀 String）。
 */
public abstract class AbstractSegmentIdGenerator<T> implements IIdGenerator<T> {

    private final String bizKey;
    private final IIdSegmentAllocator allocator;
    private volatile IdSegment segment;

    protected AbstractSegmentIdGenerator(String bizKey, IIdSegmentAllocator allocator) {
        this.bizKey = bizKey;
        this.allocator = allocator;
    }

    /** 子类决定 Long → T 的转换规则。 */
    protected abstract T convert(long rawId);

    @Override
    public synchronized T nextId() {
        IdSegment seg = this.segment;
        if (seg == null || !seg.hasNext()) {
            seg = this.segment = allocator.allocateNext(bizKey);
        }
        this.segment = seg.take();
        return convert(seg.current());
    }

    @Override
    public List<T> nextIds(int count) {
        List<T> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            result.add(nextId());
        }
        return result;
    }

    @Override
    public String bizKey() {
        return bizKey;
    }
}
