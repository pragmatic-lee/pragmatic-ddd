package io.pragmatic.ddd.base.id;

/** 产出带前缀 / 格式 String 标识的生成器，如 "ORD-00000001"。 */
public class StringSegmentIdGenerator extends AbstractSegmentIdGenerator<String> {

    private final String format;

    public StringSegmentIdGenerator(String bizKey, IIdSegmentAllocator allocator, String format) {
        super(bizKey, allocator);
        this.format = format;
    }

    @Override
    protected String convert(long rawId) {
        return String.format(format, rawId);
    }
}
