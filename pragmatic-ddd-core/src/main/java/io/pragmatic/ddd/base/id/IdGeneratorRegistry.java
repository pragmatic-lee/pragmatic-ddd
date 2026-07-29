package io.pragmatic.ddd.base.id;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 多生成器注册中心：一个 bizKey → 一个独立 IIdGenerator 实例。
 * 这就是「不是一个全局生成器」的落地处——每个渠道各持一个实例，互不干扰。
 */
public class IdGeneratorRegistry {

    private final Map<String, IIdGenerator<?>> generators = new ConcurrentHashMap<>();

    /** 直接注册一个生成器实例（按 bizKey 隔离）。 */
    public void register(IIdGenerator<?> generator) {
        generators.put(generator.bizKey(), generator);
    }

    /** 依据定义构建并注册（按 IdType 选择具体生成器）。 */
    public void register(IdGeneratorDefinition def, IIdSegmentAllocator allocator) {
        IIdGenerator<?> generator = switch (def.getIdType()) {
            case LONG -> new LongSegmentIdGenerator(def.getBizKey(), allocator);
            case STRING -> new StringSegmentIdGenerator(def.getBizKey(), allocator, def.getFormat());
        };
        register(generator);
    }

    /** 取得某渠道的生成器。 */
    @SuppressWarnings("unchecked")
    public <T> IIdGenerator<T> get(String bizKey) {
        IIdGenerator<?> g = generators.get(bizKey);
        if (g == null) {
            throw new IllegalArgumentException("未注册的 ID 生成器 bizKey=" + bizKey);
        }
        return (IIdGenerator<T>) g;
    }

    /** 便捷方法：直接取号。 */
    public <T> T nextId(String bizKey) {
        IIdGenerator<T> generator = get(bizKey);
        return generator.nextId();
    }
}
