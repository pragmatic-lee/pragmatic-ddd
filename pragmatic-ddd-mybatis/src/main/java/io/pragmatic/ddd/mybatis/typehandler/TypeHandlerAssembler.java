package io.pragmatic.ddd.mybatis.typehandler;

import io.pragmatic.ddd.base.IValueObject;
import io.pragmatic.ddd.mybatis.spi.JsonSerializer;
import io.pragmatic.ddd.mybatis.typehandler.enums.EnumRule;
import io.pragmatic.ddd.mybatis.typehandler.enums.EnumValueResolver;
import io.pragmatic.ddd.mybatis.typehandler.json.Fastjson2JsonSerializer;
import io.pragmatic.ddd.mybatis.typehandler.json.JdbcJsonValue;
import io.pragmatic.ddd.mybatis.typehandler.list.CollectionElementTypeConfig;
import io.pragmatic.ddd.mybatis.typehandler.list.CollectionMapping;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.TypeHandler;
import org.apache.ibatis.type.TypeReference;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 装配器：把多份 {@link TypeHandlerDeclaration} 合并为唯一的 {@link TypeHandlerContext}，
 * 并在合并期完成全部声明冲突校验（fail-fast），杜绝运行期静默覆盖。
 *
 * <p>MyBatis 的 {@code TypeHandlerRegistry} 对 {@code List.class} 只有全局单槽（后注册覆盖前注册），
 * 因此一个 {@code Configuration} 只能接受一份上下文；本类是「多份声明 → 一份上下文」的唯一收敛点。</p>
 *
 * @author wizard-lee
 */
public final class TypeHandlerAssembler {

    /**
     * 便捷装配：内部构建唯一的枚举解析器与唯一的 JSON 序列化器。
     *
     * @param declarations  各聚合的类型声明（允许为空集合）
     * @param jdbcJsonValue JDBC JSON 方言适配器
     * @return 唯一的装配上下文
     * @throws IllegalStateException 声明冲突或作用域不受支持时抛出
     */
    public static TypeHandlerContext assemble(Collection<TypeHandlerDeclaration> declarations,
                                              JdbcJsonValue jdbcJsonValue) {
        List<TypeHandlerDeclaration> checked = requireDefaultScope(declarations);
        Map<Class<?>, EnumRule> enumRules = mergeEnumRules(checked);
        Collection<Class<?>> voTypes = mergeVoTypes(checked, enumRules);
        List<CollectionMapping> collections = mergeCollectionMappings(checked, enumRules);
        List<CustomTypeHandlerMapping> customHandlers =
                mergeCustomHandlers(checked, enumRules, voTypes, collections);

        EnumValueResolver resolver = new EnumValueResolver();
        resolver.registerAll(enumRules);
        CollectionElementTypeConfig collectionConfig = CollectionElementTypeConfig.from(collections, resolver);
        JsonSerializer serializer = new Fastjson2JsonSerializer(resolver, enumRules);
        return new TypeHandlerContext(
                resolver,
                serializer,
                jdbcJsonValue,
                enumRules,
                voTypes,
                collectionConfig,
                customHandlers);
    }

    /**
     * 扩展装配：使用调用方提供的枚举解析器与序列化器，仍完成作用域与声明冲突校验。
     *
     * @param declarations  各聚合的类型声明（允许为空集合）
     * @param resolver      枚举解析器（全局唯一）
     * @param serializer    JSON 序列化器（全局唯一，须使用同一 resolver）
     * @param jdbcJsonValue JDBC JSON 方言适配器
     * @return 唯一的装配上下文
     * @throws IllegalStateException 声明冲突或作用域不受支持时抛出
     */
    public static TypeHandlerContext assemble(Collection<TypeHandlerDeclaration> declarations,
                                              EnumValueResolver resolver,
                                              JsonSerializer serializer,
                                              JdbcJsonValue jdbcJsonValue) {
        List<TypeHandlerDeclaration> checked = requireDefaultScope(declarations);
        Map<Class<?>, EnumRule> enumRules = mergeEnumRules(checked);
        Collection<Class<?>> voTypes = mergeVoTypes(checked, enumRules);
        List<CollectionMapping> collections = mergeCollectionMappings(checked, enumRules);
        List<CustomTypeHandlerMapping> customHandlers =
                mergeCustomHandlers(checked, enumRules, voTypes, collections);

        resolver.registerAll(enumRules);
        CollectionElementTypeConfig collectionConfig = CollectionElementTypeConfig.from(collections, resolver);
        return new TypeHandlerContext(
                resolver,
                serializer,
                jdbcJsonValue,
                enumRules,
                voTypes,
                collectionConfig,
                customHandlers);
    }

    private static List<TypeHandlerDeclaration> requireDefaultScope(Collection<TypeHandlerDeclaration> declarations) {
        if (declarations == null || declarations.isEmpty()) {
            return List.of();
        }
        List<TypeHandlerDeclaration> checked = List.copyOf(declarations);
        for (TypeHandlerDeclaration declaration : checked) {
            if (!TypeHandlerDeclaration.DEFAULT_SCOPE.equals(declaration.scope())) {
                throw new IllegalStateException(
                        "多数据源作用域尚未接入装配入口：声明 scope='" + declaration.scope()
                                + "'，当前仅支持 '" + TypeHandlerDeclaration.DEFAULT_SCOPE + "'。"
                                + "多数据源装配需按 scope 分组产出各自独立的 TypeHandlerContext。");
            }
        }
        return checked;
    }

    private static Map<Class<?>, EnumRule> mergeEnumRules(List<TypeHandlerDeclaration> declarations) {
        Map<Class<?>, EnumRule> merged = new LinkedHashMap<>();
        for (TypeHandlerDeclaration declaration : declarations) {
            for (Map.Entry<Class<?>, EnumRule> entry : declaration.enumRules().entrySet()) {
                Class<?> type = entry.getKey();
                EnumRule rule = entry.getValue();
                requireEnumType(type);
                EnumRule previous = merged.putIfAbsent(type, rule);
                requireSameRule(type, previous, rule);
            }
        }
        return Map.copyOf(merged);
    }

    private static Collection<Class<?>> mergeVoTypes(List<TypeHandlerDeclaration> declarations,
                                                     Map<Class<?>, EnumRule> enumRules) {
        Set<Class<?>> merged = new LinkedHashSet<>();
        for (TypeHandlerDeclaration declaration : declarations) {
            for (Class<?> type : declaration.voTypes()) {
                requireValueObject(type, enumRules);
                merged.add(type);
            }
        }
        return List.copyOf(merged);
    }

    private static List<CollectionMapping> mergeCollectionMappings(List<TypeHandlerDeclaration> declarations,
                                                                  Map<Class<?>, EnumRule> enumRules) {
        List<CollectionMapping> merged = new ArrayList<>();
        Map<String, CollectionMapping> declared = new LinkedHashMap<>();
        for (TypeHandlerDeclaration declaration : declarations) {
            for (CollectionMapping mapping : declaration.collections()) {
                requireEnumElementDeclared(mapping, enumRules);
                requireSameConverter(declared.putIfAbsent(mapping.lookupKey(), mapping), mapping);
                merged.add(mapping);
            }
        }
        return merged;
    }

    /**
     * 合并自定义 handler 配对：必须在三通道合并完成后执行（R11 需要三通道的最终键集合）。
     * 校验顺序 R11 → R12 → R13；同 javaType 同 handler 实例幂等通过。
     */
    private static List<CustomTypeHandlerMapping> mergeCustomHandlers(List<TypeHandlerDeclaration> declarations,
                                                                       Map<Class<?>, EnumRule> enumRules,
                                                                       Collection<Class<?>> voTypes,
                                                                       List<CollectionMapping> collections) {
        boolean listChannelOccupied = !collections.isEmpty();
        List<CustomTypeHandlerMapping> merged = new ArrayList<>();
        Map<Class<?>, TypeHandler<?>> seen = new LinkedHashMap<>();
        for (TypeHandlerDeclaration declaration : declarations) {
            for (CustomTypeHandlerMapping mapping : declaration.customHandlers()) {
                requireNotInFrameworkChannels(mapping, enumRules, voTypes, listChannelOccupied);
                TypeHandler<?> previous = seen.putIfAbsent(mapping.javaType(), mapping.handler());
                requireDistinctHandler(previous, mapping);
                requireGenericTypeMatch(mapping);
                if (previous == null) {
                    merged.add(mapping);
                }
            }
        }
        return List.copyOf(merged);
    }

    private static void requireNotInFrameworkChannels(CustomTypeHandlerMapping mapping,
                                                      Map<Class<?>, EnumRule> enumRules,
                                                      Collection<Class<?>> voTypes,
                                                      boolean listChannelOccupied) {
        Class<?> javaType = mapping.javaType();
        if (enumRules.containsKey(javaType)) {
            throw new IllegalStateException("自定义 handler 与枚举通道冲突：" + javaType.getName()
                    + " 已声明 EnumRule，持久化策略请调整 enumRules；确需自定义 handler，"
                    + "请从所有声明的 enumRules 中移除该类型。冲突来源：" + mapping);
        }
        if (voTypes.contains(javaType)) {
            throw new IllegalStateException("自定义 handler 与值对象通道冲突：" + javaType.getName()
                    + " 已走 JSON 通道，序列化定制请实现自定义 JsonSerializer 并经装配器扩展入口注入。"
                    + "冲突来源：" + mapping);
        }
        if (listChannelOccupied && List.class == javaType) {
            throw new IllegalStateException("自定义 handler 与集合通道冲突：List.class 已由 ListTypeHandler 统一接管，"
                    + "元素转换请用 ElementConverter，不支持整类替换。冲突来源：" + mapping);
        }
    }

    private static void requireDistinctHandler(TypeHandler<?> previous, CustomTypeHandlerMapping current) {
        if (previous == null || previous == current.handler()) {
            return;
        }
        throw new IllegalStateException("自定义 handler 冲突：" + current.javaType().getName()
                + " 被绑定到两个不同的 handler 实例 —— " + previous + " 与 " + current.handler()
                + "。同一 javaType 必须使用同一 handler 实例，或只在一处声明。");
    }

    private static void requireGenericTypeMatch(CustomTypeHandlerMapping mapping) {
        Class<?> argument = resolveHandlerTypeArgument(mapping.handler().getClass());
        if (argument == null) {
            return;
        }
        Class<?> javaType = mapping.javaType();
        if (argument == javaType || argument.isAssignableFrom(javaType) || javaType.isAssignableFrom(argument)) {
            return;
        }
        throw new IllegalStateException("自定义 handler 泛型与 javaType 不匹配：" + mapping.javaType().getName()
                + " 的 handler 类型实参为 " + argument.getName() + "，二者无同型或子类型关系。"
                + "请核对 handler 的 TypeHandler<T> 泛型声明。冲突来源：" + mapping);
    }

    /**
     * 沿 handler 类层次解析 TypeHandler&lt;T&gt; 的首个类型实参；
     * 解析不出（raw 实现 / 匿名类 / 类型变量）返回 null，由调用方跳过校验。
     */
    private static Class<?> resolveHandlerTypeArgument(Class<?> handlerClass) {
        for (Class<?> current = handlerClass;
             current != null && current != Object.class;
             current = current.getSuperclass()) {
            Type superclass = current.getGenericSuperclass();
            if (superclass instanceof ParameterizedType parameterized && isHandlerRawType(parameterized)) {
                return asResolvableClass(parameterized);
            }
            for (Type implemented : current.getGenericInterfaces()) {
                if (implemented instanceof ParameterizedType parameterized && isHandlerRawType(parameterized)) {
                    return asResolvableClass(parameterized);
                }
            }
        }
        return null;
    }

    private static boolean isHandlerRawType(ParameterizedType parameterized) {
        return parameterized.getRawType() == TypeHandler.class
                || parameterized.getRawType() == TypeReference.class
                || parameterized.getRawType() == BaseTypeHandler.class;
    }

    private static Class<?> asResolvableClass(ParameterizedType parameterized) {
        if (parameterized.getActualTypeArguments()[0] instanceof Class<?> argument) {
            return argument;
        }
        return null;
    }

    private static void requireEnumType(Class<?> type) {
        if (Enum.class.isAssignableFrom(type)) {
            return;
        }
        throw new IllegalStateException("枚举声明非法：" + type.getName()
                + " 不是枚举类型，不能出现在 enumRules 中。");
    }

    private static void requireSameRule(Class<?> type, EnumRule previous, EnumRule current) {
        if (previous == null || previous == current) {
            return;
        }
        throw new IllegalStateException("枚举持久化策略冲突：" + type.getName()
                + " 被声明为 " + previous + " 与 " + current + " 两种策略。"
                + "同一枚举必须在所有聚合的 TypeHandlerDeclaration 中保持一致的 EnumRule。");
    }

    private static void requireValueObject(Class<?> type, Map<Class<?>, EnumRule> enumRules) {
        if (!IValueObject.class.isAssignableFrom(type)) {
            throw new IllegalStateException("值对象声明非法：" + type.getName()
                    + " 未实现 IValueObject，不能出现在 voTypes 中（JSON 列映射要求实现该接口）。");
        }
        if (enumRules.containsKey(type)) {
            throw new IllegalStateException("声明矛盾：" + type.getName()
                    + " 同时出现在 enumRules 与 voTypes 中；枚举走枚举通道、值对象走 JSON 通道，二者只能选其一。");
        }
    }

    private static void requireEnumElementDeclared(CollectionMapping mapping, Map<Class<?>, EnumRule> enumRules) {
        Class<?> elementType = mapping.elementType();
        if (!Enum.class.isAssignableFrom(elementType)) {
            return;
        }
        if (enumRules.containsKey(elementType)) {
            return;
        }
        throw new IllegalStateException("集合元素枚举未声明持久化策略：" + mapping
                + " 的元素类型 " + elementType.getName() + " 未出现在 enumRules 中。"
                + "请在 TypeHandlerDeclaration 中显式声明其 EnumRule（不再隐式按 CODE 处理）。");
    }

    private static void requireSameConverter(CollectionMapping previous, CollectionMapping current) {
        if (previous == null) {
            return;
        }
        if (previous.converter() == current.converter()) {
            return;
        }
        throw new IllegalStateException("集合列标签转换器冲突：'" + current.lookupKey()
                + "' 被声明为两种不同的 ElementConverter —— " + previous + " 与 " + current
                + "。同一列标签只能有一个转换器。");
    }

    private TypeHandlerAssembler() {
    }
}
