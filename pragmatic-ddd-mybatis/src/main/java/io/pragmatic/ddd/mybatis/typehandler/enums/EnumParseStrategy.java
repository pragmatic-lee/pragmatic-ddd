package io.pragmatic.ddd.mybatis.typehandler.enums;

import io.pragmatic.ddd.base.IEnumValue;

/**
 * 枚举解析策略——集中化反序列化的统一抽象。
 * 所有枚举不再自带 parse，反查统一委托给具体策略实现。
 * 对应设计文档 Step 6（提案 §5.0.6(1)）。
 */
public interface EnumParseStrategy {
    /** 由原始列值反查枚举常量；未命中抛 {@link IllegalArgumentException}。entry 为启动期预建的索引条目。 */
    <E extends Enum<E>> E resolve(Class<E> type, Object raw, EnumValueResolver.ResolverEntry entry);

    /** 该策略是否适用于给定枚举（如读 getValue 要求实现 IEnumValue）。 */
    default boolean supports(Class<?> type) {
        return true;
    }
}

/** 按枚举常量名 {@code Enum.name()} —— 适用于所有枚举，零依赖。 */
final class EnumNameStrategy implements EnumParseStrategy {
    static final EnumNameStrategy INSTANCE = new EnumNameStrategy();

    @Override
    public <E extends Enum<E>> E resolve(Class<E> type, Object raw, EnumValueResolver.ResolverEntry entry) {
        return Enum.valueOf(type, String.valueOf(raw).trim());
    }
}

/** 按声明顺序 ordinal —— 适用于所有枚举。 */
final class EnumOrdinalStrategy implements EnumParseStrategy {
    static final EnumOrdinalStrategy INSTANCE = new EnumOrdinalStrategy();

    @Override
    public <E extends Enum<E>> E resolve(Class<E> type, Object raw, EnumValueResolver.ResolverEntry entry) {
        E[] values = type.getEnumConstants();
        int idx = raw instanceof Number ? ((Number) raw).intValue() : Integer.parseInt(raw.toString());
        if (idx < 0 || idx >= values.length) throw new IllegalArgumentException("ordinal 越界: " + raw);
        return values[idx];
    }
}

/** 按业务 code（getValue()）—— 要求实现 IEnumValue；可挂 EnumCodec 做自定义 code 提取 / 归一化。 */
final class EnumValueStrategy implements EnumParseStrategy {
    static final EnumValueStrategy INSTANCE = new EnumValueStrategy();

    @Override
    public <E extends Enum<E>> E resolve(Class<E> type, Object raw, EnumValueResolver.ResolverEntry entry) {
        if (!IEnumValue.class.isAssignableFrom(type))
            throw new IllegalArgumentException(type + " 未实现 IEnumValue,无法按 value 解析");
        Object key = (entry.codec() != null) ? entry.codec().normalize(raw) : raw;
        @SuppressWarnings("unchecked")
        E constant = (E) entry.valueIndex().get(key);   // 查启动期预建索引, O(1)
        if (constant == null) throw new IllegalArgumentException("未找到 " + type.getSimpleName() + " 的 code=" + raw);
        return constant;
    }

    @Override
    public boolean supports(Class<?> type) {
        return IEnumValue.class.isAssignableFrom(type);
    }
}

/** 按展示名 getName()（可选，便于校验 / 测试）。 */
final class EnumLabelStrategy implements EnumParseStrategy {
    static final EnumLabelStrategy INSTANCE = new EnumLabelStrategy();

    @Override
    public <E extends Enum<E>> E resolve(Class<E> type, Object raw, EnumValueResolver.ResolverEntry entry) {
        E constant = (E) entry.labelIndex().get(String.valueOf(raw));
        if (constant == null) throw new IllegalArgumentException("未找到 " + type.getSimpleName() + " 的 name=" + raw);
        return constant;
    }
}
