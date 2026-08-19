package io.pragmatic.ddd.mybatis.typehandler.enums;

import io.pragmatic.ddd.base.IEnumValue;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 枚举常量索引构建器——在启动期集中注册时为每个枚举预建映射，
 * 结果存进 {@code EnumValueResolver.ResolverEntry}，运行期零反射、O(1) 查表。
 *
 * <p>重复 code / name 在构建期即 fail-fast。</p>
 *
 * @author wizard-lee
 */
final class EnumValueIndex {

    /** 业务 code（getValue() / codec.toCode）→ 常量；仅对有 IEnumValue 的枚举构建。 */
    static Map<Object, Enum<?>> buildValueIndex(Class<? extends Enum<?>> type, EnumCodec codec) {
        Map<Object, Enum<?>> m = new LinkedHashMap<>();
        for (Enum<?> e : type.getEnumConstants()) {
            Object code = (codec != null) ? codec.toCode(e) : ((IEnumValue<?, ?>) e).getValue();
            if (m.putIfAbsent(code, e) != null)
                throw new IllegalArgumentException("重复 code 枚举: " + type.getSimpleName() + " code=" + code);
        }
        return m;
    }

    /** 展示名 getName() → 常量；仅对有 IEnumValue 的枚举构建。 */
    static Map<String, Enum<?>> buildLabelIndex(Class<? extends Enum<?>> type) {
        Map<String, Enum<?>> m = new LinkedHashMap<>();
        for (Enum<?> e : type.getEnumConstants()) {
            String name = ((IEnumValue<?, ?>) e).getName();
            if (m.putIfAbsent(name, e) != null)
                throw new IllegalArgumentException("重复 name 枚举: " + type.getSimpleName() + " name=" + name);
        }
        return m;
    }

    /** 声明顺序 ordinal → 常量；适用于所有枚举。 */
    static Map<Integer, Enum<?>> buildOrdinalIndex(Class<? extends Enum<?>> type) {
        Map<Integer, Enum<?>> m = new LinkedHashMap<>();
        Enum<?>[] values = type.getEnumConstants();
        for (int i = 0; i < values.length; i++) m.put(i, values[i]);
        return m;
    }
}
