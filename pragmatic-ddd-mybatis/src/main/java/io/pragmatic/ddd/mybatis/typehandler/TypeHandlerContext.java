package io.pragmatic.ddd.mybatis.typehandler;

import io.pragmatic.ddd.mybatis.spi.JsonSerializer;
import io.pragmatic.ddd.mybatis.typehandler.enums.EnumRule;
import io.pragmatic.ddd.mybatis.typehandler.enums.EnumTypeHandlerAutoConfigurer;
import io.pragmatic.ddd.mybatis.typehandler.enums.EnumValueResolver;
import io.pragmatic.ddd.mybatis.typehandler.json.JdbcJsonValue;
import io.pragmatic.ddd.mybatis.typehandler.json.JsonTypeHandlerAutoConfigurer;
import io.pragmatic.ddd.mybatis.typehandler.list.CollectionElementTypeConfig;
import io.pragmatic.ddd.mybatis.typehandler.list.ListTypeHandlerAutoConfigurer;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.TypeHandler;
import org.apache.ibatis.type.TypeHandlerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 装配上下文：集中持有枚举策略、VO 类型、集合列配置、自定义 handler 与共享组件，统一一次构建全部 TypeHandler。
 * 零 Spring 依赖，纯手动装配；经 {@link #registrations()} 导出注册项、{@link #registerInto(Configuration)} 装配。
 *
 * <p><b>唯一性约束</b>：MyBatis 的 {@code TypeHandlerRegistry} 对 {@code List.class} 只有全局单槽，
 * 后注册覆盖前注册，因此一个 {@code Configuration} 只能装配一份本上下文。应用侧不得直接 {@code new}，
 * 必须经 {@link TypeHandlerAssembler} 合并多份 {@link TypeHandlerDeclaration} 后产出，
 * 否则会绕过声明冲突校验并重新引入多实例互相覆盖。</p>
 *
 * <p><b>自定义通道</b>：{@code customHandlers} 为声明侧自定义 handler（合并期 R11–R14 校验后并入），
 * 注册语义为「框架三通道补位」。handler 实例必须无状态，且不得依赖框架共享组件
 * （{@code EnumValueResolver} / {@code JsonSerializer} / {@code JdbcJsonValue} 不注入自定义 handler）；
 * 触碰 MyBatis 内置默认映射时在装配期打 WARN（设计决策 D8）。</p>
 *
 * <p>{@link #resolver()} 在装配完成后视为冻结：之后不得再变更策略，以免运行期污染全局枚举策略。</p>
 *
 * @author wizard-lee
 */
public record TypeHandlerContext(EnumValueResolver resolver,
                                 JsonSerializer serializer,
                                 JdbcJsonValue jdbcJsonValue,
                                 Map<Class<?>, EnumRule> enumRules,
                                 Collection<Class<?>> voTypes,
                                 CollectionElementTypeConfig collections,
                                 Collection<CustomTypeHandlerMapping> customHandlers) {

    private static final Logger LOG = LoggerFactory.getLogger(TypeHandlerContext.class);

    /**
     * 导出全部注册项（javaType ↔ handler 配对），四通道构建结果的统一出口。
     *
     * @return 只读注册项集合
     */
    public Collection<TypeHandlerRegistration> registrations() {
        return buildTypeHandlerMap().entrySet().stream()
                .map(e -> new TypeHandlerRegistration(e.getKey(), e.getValue()))
                .toList();
    }

    /**
     * 装配入口：将全部注册项灌入 {@code Configuration}，构建阶段与 XML Mapper 装配同步。
     *
     * @param configuration 目标 Configuration
     */
    public void registerInto(Configuration configuration) {
        TypeHandlerRegistry registry = configuration.getTypeHandlerRegistry();
        for (TypeHandlerRegistration registration : registrations()) {
            warnIfOverridingBuiltin(registry, registration.javaType());
            // javaType 与 handler 由构建阶段配对生成，类型一致；因 record 组件为两个独立通配符捕获，
            // 无法在编译期统一为同一 T，故以 raw 方式调用 register，消除运行时类型推断差异。
            @SuppressWarnings({"unchecked", "rawtypes"})
            Class type = registration.javaType();
            registry.register(type, (TypeHandler) registration.handler());
        }
    }

    private Map<Class<?>, TypeHandler<?>> buildTypeHandlerMap() {
        Map<Class<?>, TypeHandler<?>> map = new LinkedHashMap<>();
        EnumTypeHandlerAutoConfigurer.register(resolver, enumRules, map);
        JsonTypeHandlerAutoConfigurer.register(serializer, jdbcJsonValue, voTypes, map);
        ListTypeHandlerAutoConfigurer.register(serializer, jdbcJsonValue, collections, map);
        // 自定义通道最后追加：合并期 R11 已保证键与框架三通道不相交，此处不会覆盖任何框架注册。
        customHandlers.forEach(mapping -> map.put(mapping.javaType(), mapping.handler()));
        return Map.copyOf(map);
    }

    /**
     * 自定义通道触碰 MyBatis 内置默认 handler 时打 WARN，让全局影响显性化（设计决策 D8）。
     * 框架三通道的键（枚举 / VO / 有集合声明时的 List.class）不适用。
     */
    private void warnIfOverridingBuiltin(TypeHandlerRegistry registry, Class<?> javaType) {
        if (isFrameworkChannelKey(javaType) || !registry.hasTypeHandler(javaType)) {
            return;
        }
        LOG.warn("自定义 TypeHandler 将覆盖 MyBatis 内置默认映射：javaType={}，该类型全局生效，请确认影响面。",
                javaType.getName());
    }

    private boolean isFrameworkChannelKey(Class<?> javaType) {
        return enumRules.containsKey(javaType)
                || voTypes.contains(javaType)
                || (!collections.columnListTypes().isEmpty() && List.class == javaType);
    }
}
