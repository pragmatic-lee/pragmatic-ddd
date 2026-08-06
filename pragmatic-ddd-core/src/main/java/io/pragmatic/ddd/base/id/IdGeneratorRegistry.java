package io.pragmatic.ddd.base.id;

import io.pragmatic.ddd.config.ConfigurationBinder;
import io.pragmatic.ddd.config.IConfigurationSource;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 多生成器注册中心：一个 bizKey 对应一个独立的 IIdGenerator 实例，各渠道互不干扰。
 *
 * @author wizard-lee
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

    /**
     * 从配置源按 {@code id.{bizKey}.*} 前缀批量加载并注册生成器（兼容并收敛既有配置）。
     * 键约定：id.{bizKey}.start-id / id.{bizKey}.step / id.{bizKey}.id-type / id.{bizKey}.format。
     *
     * @param source   配置源
     * @param allocator 号段分配器（由调用方提供，框架不预设存储后端）
     */
    public void loadFrom(IConfigurationSource source, IIdSegmentAllocator allocator) {
        Set<String> bizKeys = source.keys().stream()
                .filter(k -> k.startsWith("id."))
                .map(k -> k.substring(3).split("\\.")[0])
                .collect(Collectors.toSet());
        for (String bizKey : bizKeys) {
            IdGeneratorDefinition def = ConfigurationBinder.bind(source, "id." + bizKey, IdGeneratorDefinition.class);
            def.setBizKey(bizKey);
            register(def, allocator);
        }
    }
}
