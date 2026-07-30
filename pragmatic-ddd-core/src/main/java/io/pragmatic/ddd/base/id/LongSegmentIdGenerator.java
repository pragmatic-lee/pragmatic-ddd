package io.pragmatic.ddd.base.id;

/**
 * 产出纯数字 Long 标识的生成器。
 *
 * @author wizard-lee
 */
public class LongSegmentIdGenerator extends AbstractSegmentIdGenerator<Long> {

    public LongSegmentIdGenerator(String bizKey, IIdSegmentAllocator allocator) {
        super(bizKey, allocator);
    }

    @Override
    protected Long convert(long rawId) {
        return rawId;
    }
}
