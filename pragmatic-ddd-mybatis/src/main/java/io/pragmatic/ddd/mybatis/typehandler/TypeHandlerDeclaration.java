package io.pragmatic.ddd.mybatis.typehandler;

import io.pragmatic.ddd.mybatis.typehandler.enums.EnumRule;
import io.pragmatic.ddd.mybatis.typehandler.list.CollectionMapping;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 复杂类型声明：聚合侧只描述本聚合涉及的枚举、值对象与集合列，不描述装配机制。
 * 由 {@link TypeHandlerAssembler} 汇总合并后产出唯一的 {@link TypeHandlerContext}。
 *
 * <p>声明天然可多份（每个聚合一份），但不得持有 {@code EnumValueResolver}、{@code JsonSerializer}、
 * {@code JdbcJsonValue} 等运行时组件——这些组件全局唯一，由装配器统一构建。</p>
 *
 * @author wizard-lee
 */
public record TypeHandlerDeclaration(String scope,
                                     Map<Class<?>, EnumRule> enumRules,
                                     Collection<Class<?>> voTypes,
                                     Collection<CollectionMapping> collections) {

    /** 单数据源默认作用域；多数据源场景预留。 */
    public static final String DEFAULT_SCOPE = "default";

    public TypeHandlerDeclaration {
        scope = Optional.ofNullable(scope)
                .filter(value -> !value.isBlank())
                .map(String::trim)
                .orElse(DEFAULT_SCOPE);
        enumRules = Optional.ofNullable(enumRules)
                .map(Map::copyOf)
                .orElseGet(Map::of);
        voTypes = Optional.ofNullable(voTypes)
                .map(List::copyOf)
                .orElseGet(List::of);
        collections = Optional.ofNullable(collections)
                .map(List::copyOf)
                .orElseGet(List::of);
    }

    /**
     * 按默认作用域构建声明。
     *
     * @param enumRules   枚举类 → 持久化策略
     * @param voTypes     JSON 值对象类型
     * @param collections 集合列映射声明
     * @return 声明实例
     */
    public static TypeHandlerDeclaration of(Map<Class<?>, EnumRule> enumRules,
                                           Collection<Class<?>> voTypes,
                                           Collection<CollectionMapping> collections) {
        return new TypeHandlerDeclaration(DEFAULT_SCOPE, enumRules, voTypes, collections);
    }

    /** 空声明：不登记任何类型。 */
    public static TypeHandlerDeclaration empty() {
        return of(Map.of(), List.of(), List.of());
    }

    /**
     * 复制一份声明并替换作用域，供多数据源场景使用。
     *
     * @param scope 作用域标识
     * @return 新声明
     */
    public TypeHandlerDeclaration withScope(String scope) {
        return new TypeHandlerDeclaration(scope, enumRules, voTypes, collections);
    }
}
