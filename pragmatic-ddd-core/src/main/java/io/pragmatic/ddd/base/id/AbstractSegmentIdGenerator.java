package io.pragmatic.ddd.base.id;

import java.util.ArrayList;
import java.util.List;

/**
 * 号段模式生成器基类：内存持有当前号段，按需向 IIdSegmentAllocator 申请下一段。
 * 子类只需提供 Long → T 的转换规则（纯数字 / 带前缀 String）。
 *
 * @param <T> 标识类型
 * @author wizard-lee
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

    /** 生成下一个标识：当前号段耗尽时向分配器申请下一段。 */
    @Override
    public synchronized T nextId() {
        IdSegment seg = this.segment;
        if (seg == null || !seg.hasNext()) {
            seg = this.segment = allocator.allocateNext(bizKey);
        }
        this.segment = seg.take();
        return convert(seg.current());
    }

    /** 批量生成 count 个连续标识。 */
    @Override
    public List<T> nextIds(int count) {
        List<T> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            result.add(nextId());
        }
        return result;
    }

    /** 返回业务键（渠道）。 */
    @Override
    public String bizKey() {
        return bizKey;
    }
}
