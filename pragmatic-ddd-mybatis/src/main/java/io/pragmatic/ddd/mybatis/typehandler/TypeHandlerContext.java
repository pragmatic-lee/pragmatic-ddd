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

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 装配上下文：集中持有枚举策略、VO 类型与共享组件，统一一次构建全部 TypeHandler。
 * 零 Spring 依赖，纯手动装配；经 {@link #registrations()} 导出注册项、{@link #registerInto(Configuration)} 装配。
 *
 * @author wizard-lee
 */
public record TypeHandlerContext(EnumValueResolver resolver,
                                 JsonSerializer serializer,
                                 JdbcJsonValue jdbcJsonValue,
                                 Map<Class<?>, EnumRule> enumRules,
                                 Collection<Class<?>> voTypes,
                                 CollectionElementTypeConfig collections) {

    /**
     * 导出全部注册项（javaType ↔ handler 配对），三通道构建结果的统一出口。
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
        return Map.copyOf(map);
    }
}
