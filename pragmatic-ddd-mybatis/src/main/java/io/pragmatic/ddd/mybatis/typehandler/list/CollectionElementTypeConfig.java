package io.pragmatic.ddd.mybatis.typehandler.list;

import io.pragmatic.ddd.mybatis.typehandler.enums.EnumValueResolver;

import java.lang.Enum;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 集合字段配置中心：从若干 {@link CollectionMapping} 构建运行期查表索引，并在构建期对同名列标签 + 不同类型做 fail-fast 冲突校验。
 *
 * @author wizard-lee
 */
public final class CollectionElementTypeConfig {

    private final Map<String, Type> columnListTypes;      // label -> List<E> 参数化类型
    private final Map<String, ElementConverter> converters; // label -> 元素转换器
    private final Map<String, CollectionMapping> sources;   // label -> 原始声明（诊断）

    private CollectionElementTypeConfig(Map<String, Type> c,
                                       Map<String, ElementConverter> conv,
                                       Map<String, CollectionMapping> src) {
        this.columnListTypes = Map.copyOf(c);
        this.converters = Map.copyOf(conv);
        this.sources = Map.copyOf(src);
    }

    /**
     * 从映射声明集合构建配置中心。
     *
     * @param mappings 集合字段映射声明（原生 Java 或 Spring 绑定后传入）
     * @param resolver 共享枚举解析注册表（枚举元素类型在此预注册，保证与单列枚举形态一致）
     * @throws IllegalStateException 当相同列标签被声明为两种不同元素类型（未隔离的同名列冲突）
     */
    public static CollectionElementTypeConfig from(Collection<CollectionMapping> mappings,
                                                 EnumValueResolver resolver) {
        Map<String, Type> c = new LinkedHashMap<>();
        Map<String, ElementConverter> conv = new LinkedHashMap<>();
        Map<String, CollectionMapping> src = new LinkedHashMap<>();

        for (CollectionMapping m : mappings) {
            String label = m.lookupKey();

            // 冲突校验：同 label 已存在且元素类型不同 -> 启动期 fail-fast
            CollectionMapping prev = src.get(label);
            if (prev != null && !prev.elementType().equals(m.elementType())) {
                throw new IllegalStateException(
                        "集合列标签冲突: 结果集列标签 '" + label + "' 被声明为两种不同元素类型 —— "
                                + prev + " 与 " + m + "。请通过 SQL AS 别名(CollectionMapping.columnLabel)或 table 隔离区分, "
                                + "不要在同一查询结果中保留同名列。");
            }

            // 枚举元素类型确保已在 resolver 注册（与单列枚举 EnumRule 一致；register 幂等）
            Class<?> et = m.elementType();
            if (Enum.class.isAssignableFrom(et)) {
                resolver.register(et.asSubclass(Enum.class));
            }

            c.put(label, listTypeOf(et));
            conv.put(label, m.converter());
            src.put(label, m);
        }
        return new CollectionElementTypeConfig(c, conv, src);
    }

    public Map<String, Type> columnListTypes() {
        return columnListTypes;
    }

    public Map<String, ElementConverter> converters() {
        return converters;
    }

    public Map<String, CollectionMapping> sources() {
        return sources;
    }

    public static CollectionElementTypeConfig empty() {
        return new CollectionElementTypeConfig(Map.of(), Map.of(), Map.of());
    }

    /** 构造 List<elementType> 的 ParameterizedType（运行期按 Class 变量生成）。 */
    public static Type listTypeOf(Class<?> elementType) {
        return new ParameterizedType() {
            @Override
            public Type[] getActualTypeArguments() {
                return new Type[]{elementType};
            }

            @Override
            public Type getRawType() {
                return List.class;
            }

            @Override
            public Type getOwnerType() {
                return null;
            }

            @Override
            public String getTypeName() {
                return List.class.getTypeName() + "<" + elementType.getTypeName() + ">";
            }

            @Override
            public String toString() {
                return getTypeName();
            }
        };
    }
}
