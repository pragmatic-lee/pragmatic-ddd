package io.pragmatic.ddd.mybatis.typehandler.json;

import io.pragmatic.ddd.base.IValueObject;
import io.pragmatic.ddd.mybatis.spi.JsonSerializer;
import io.pragmatic.ddd.mybatis.typehandler.TypeHandlerContext;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.type.TypeHandlerRegistry;

/**
 * JSON 通道批量注册器（无 Spring 依赖）：在 {@link SqlSessionFactory} 构建后，
 * 程序化登记 VO 类型（与枚举登记同样无注解依赖），统一从 {@link TypeHandlerContext} 取共享依赖。
 *
 * <p>仅登记实现了 {@link IValueObject} 的类型；每个 VO 绑定一个 {@link GenericJsonTypeHandler}，
 * 由它把 VO 整体委托给 {@code TypeHandlerContext} 中的 {@link JsonSerializer} 读写原生 JSON 列。
 */
public final class JsonTypeHandlerAutoConfigurer {

    /** 主入口：从统一装配上下文取 serializer / jdbcJsonValue / voTypes，批量注册 VO 类型。 */
    public static <T> void configure(TypeHandlerContext ctx, SqlSessionFactory sqlSessionFactory) {
        JsonSerializer serializer = ctx.serializer();
        JdbcJsonValue jdbcJsonValue = ctx.jdbcJsonValue();
        TypeHandlerRegistry reg = sqlSessionFactory.getConfiguration().getTypeHandlerRegistry();
        for (Class<?> t : ctx.voTypes()) {
            if (!IValueObject.class.isAssignableFrom(t)) continue;   // 仅登记值对象
            @SuppressWarnings("unchecked")
            Class<T> vt = (Class<T>) t;
            reg.register(vt, new GenericJsonTypeHandler<>(vt, serializer, jdbcJsonValue));
        }
    }
}
