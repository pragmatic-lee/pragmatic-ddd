package io.pragmatic.ddd.mybatis.typehandler;

import io.pragmatic.ddd.mybatis.spi.JsonSerializer;
import io.pragmatic.ddd.mybatis.typehandler.enums.EnumRule;
import io.pragmatic.ddd.mybatis.typehandler.enums.EnumTypeHandlerAutoConfigurer;
import io.pragmatic.ddd.mybatis.typehandler.enums.EnumValueResolver;
import io.pragmatic.ddd.mybatis.typehandler.json.JdbcJsonValue;
import io.pragmatic.ddd.mybatis.typehandler.json.JsonTypeHandlerAutoConfigurer;
import org.apache.ibatis.session.SqlSessionFactory;

import java.util.Collection;
import java.util.Map;

/**
 * 装配上下文：集中持有枚举策略、VO 类型与共享组件，统一一次触发注册。
 *
 * <p>枚举通道（{@code UniversalEnumTypeHandler}）与 JSON 通道（{@code GenericJsonTypeHandler}）
 * 共用同一份 resolver / serializer / jdbcJsonValue，杜绝两配置器重复传参导致的策略漂移。
 * 调用方构建完 {@link SqlSessionFactory} 后，调用一次 {@link #registerInto(SqlSessionFactory)} 即可。
 *
 * <p>示例（PG 场景把 {@link JdbcJsonValue#DEFAULT} 换成 PGobject-based 实现即可）：
 * <pre>{@code
 *   EnumValueResolver resolver = new EnumValueResolver();
 *   Map<Class<?>, EnumRule> enumRules = Map.of(OrderStatus.class, EnumRule.CODE);
 *   JsonSerializer serializer = new Fastjson2JsonSerializer(resolver, enumRules);
 *   TypeHandlerContext ctx = new TypeHandlerContext(resolver, serializer,
 *           JdbcJsonValue.DEFAULT, enumRules, voTypes);
 *   ctx.registerInto(sqlSessionFactory);
 * }</pre>
 *
 * <p>零 Spring 依赖：纯手动装配，便于非 Spring 使用；Spring Boot 自动配置只需注入同一个
 * {@code TypeHandlerContext} 并调用 {@code registerInto}。
 */
public record TypeHandlerContext(EnumValueResolver resolver,
                                 JsonSerializer serializer,
                                 JdbcJsonValue jdbcJsonValue,
                                 Map<Class<?>, EnumRule> enumRules, Collection<Class<?>> voTypes) {

    /**
     * 一次性注册枚举通道 + JSON 通道；调用方在 SqlSessionFactory 构建后调用一次。
     */
    public void registerInto(SqlSessionFactory sqlSessionFactory) {
        EnumTypeHandlerAutoConfigurer.configure(this, sqlSessionFactory);
        JsonTypeHandlerAutoConfigurer.configure(this, sqlSessionFactory);
    }
}
