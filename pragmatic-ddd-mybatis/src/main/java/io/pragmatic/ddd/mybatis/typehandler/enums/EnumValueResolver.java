package io.pragmatic.ddd.mybatis.typehandler.enums;

import io.pragmatic.ddd.base.IEnumValue;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 集中化枚举解析注册表——所有枚举在启动时统一 register，反序列化只查表。
 * 取代"各枚举自带静态 parse"与"运行时按类型懒反射"两种非标做法。
 * 对应设计文档 Step 8（提案 §5.0.6(4)）。
 *
 * <p>本实现严格遵循本计划文件清单：不引入提案中的 {@code EnumCodecProvider}，
 * 直接持有默认 {@link DefaultEnumCodec} 实例。无码枚举仅构建 ordinal 索引，
 * 启动期集中登记即完成索引预建与冲突校验。
 */
public final class EnumValueResolver {

    private final EnumCodec codec;
    private final EnumRule defaultRule;

    public EnumValueResolver() {
        this(new DefaultEnumCodec());
    }

    public EnumValueResolver(EnumCodec codec) {
        this(codec, EnumRule.CODE);
    }

    public EnumValueResolver(EnumCodec codec, EnumRule defaultRule) {
        this.codec = codec;
        this.defaultRule = defaultRule;
    }

    /**
     * 枚举类 → 已注册条目（策略 + 预建索引）。启动期一次性灌满，运行期只读。
     */
    private final ConcurrentMap<Class<? extends Enum<?>>, ResolverEntry> registry = new ConcurrentHashMap<>();

    // ===== 集中注册（启动期由 EnumTypeHandlerAutoConfigurer 调用） =====
    public <E extends Enum<E>> void register(Class<E> type) {
        registry.put(type, ResolverEntry.build(type, resolveRule(type), codec));
    }

    public void registerAll(Collection<Class<?>> types) {
        for (Class<?> t : types)
            if (Enum.class.isAssignableFrom(t)) {
                register(t.asSubclass(Enum.class)); // 含无码枚举
            }
    }

    // ===== 反序列化（运行期只查表, O(1)） =====
    public <E extends Enum<E>> E resolve(Class<E> type, Object raw) {
        return resolve(type, raw, resolveRule(type));
    }

    public <E extends Enum<E>> E resolve(Class<E> type, Object raw, EnumRule rule) {
        ResolverEntry entry = registry.get(type);
        if (entry == null) entry = registerLazily(type);   // 兜底：首次 resolve 未注册时自动注册
        return strategyOf(rule).resolve(type, raw, entry);
    }

    /**
     * 便捷方法
     */
    public <E extends Enum<E> & IEnumValue<?, ?>> E byValue(Class<E> type, Object code) {
        return resolve(type, code, EnumRule.CODE);
    }

    public <E extends Enum<E>> E byName(Class<E> type, String name) {
        return resolve(type, name, EnumRule.NAME);
    }

    public <E extends Enum<E>> E byOrdinal(Class<E> type, int ordinal) {
        return resolve(type, ordinal, EnumRule.ORDINAL);
    }

    private ResolverEntry registerLazily(Class<?> type) {
        register(type.asSubclass(Enum.class));
        return registry.get(type);
    }

    EnumParseStrategy strategyOf(EnumRule rule) {
        return switch (rule) {
            case NAME -> EnumNameStrategy.INSTANCE;
            case ORDINAL -> EnumOrdinalStrategy.INSTANCE;
            case LABEL -> EnumLabelStrategy.INSTANCE;
            case CODE -> EnumValueStrategy.INSTANCE;
        };
    }

    /**
     * 读枚举类上的 @EnumMapping 或回退默认规则。由 EnumTypeHandlerAutoConfigurer 调用，故为 public。
     */
    public EnumRule resolveRule(Class<?> type) {
        EnumMapping ann = type.getAnnotation(EnumMapping.class);
        return (ann != null) ? ann.strategy() : defaultRule;
    }

    /**
     * 单枚举注册条目：启动期预建索引 + 选定策略，运行期零反射。包可见以供给策略类访问。
     */
    static final class ResolverEntry {
        final EnumCodec codec;
        final Map<Object, Enum<?>> valueIndex;
        final Map<String, Enum<?>> labelIndex;
        final Map<Integer, Enum<?>> ordinalIndex;

        static ResolverEntry build(Class<? extends Enum<?>> type, EnumRule rule, EnumCodec codec) {
            Map<Integer, Enum<?>> ordinalIndex = EnumValueIndex.buildOrdinalIndex(type);
            if (IEnumValue.class.isAssignableFrom(type)) {
                Map<Object, Enum<?>> valueIndex = EnumValueIndex.buildValueIndex(type, codec);   // 不再需要 (Class) raw cast
                Map<String, Enum<?>> labelIndex = EnumValueIndex.buildLabelIndex(type);
                return new ResolverEntry(codec, valueIndex, labelIndex, ordinalIndex);
            }
            // 无码枚举无 IEnumValue：仅 ordinal 索引可用，CODE / LABEL 策略不适用
            return new ResolverEntry(codec, new LinkedHashMap<>(), new LinkedHashMap<>(), ordinalIndex);
        }

        EnumCodec codec() {
            return codec;
        }

        Map<Object, Enum<?>> valueIndex() {
            return valueIndex;
        }

        Map<String, Enum<?>> labelIndex() {
            return labelIndex;
        }

        Map<Integer, Enum<?>> ordinalIndex() {
            return ordinalIndex;
        }

        private ResolverEntry(EnumCodec codec, Map<Object, Enum<?>> v, Map<String, Enum<?>> l, Map<Integer, Enum<?>> o) {
            this.codec = codec;
            this.valueIndex = v;
            this.labelIndex = l;
            this.ordinalIndex = o;
        }
    }
}
