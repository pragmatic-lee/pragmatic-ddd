package io.pragmatic.ddd.mybatis.typehandler.json;

import io.pragmatic.ddd.base.IValueObject;
import io.pragmatic.ddd.mybatis.spi.JsonSerializer;
import io.pragmatic.ddd.mybatis.typehandler.TypeHandlerContext;
import org.apache.ibatis.type.TypeHandler;

import java.util.Collection;
import java.util.Map;

/**
 * JSON 通道构建器（无 Spring 依赖）：构建 {@link GenericJsonTypeHandler} 并登记 javaType → handler。
 *
 * <p>仅登记实现了 {@link IValueObject} 的类型；由 {@link TypeHandlerContext#registrations()}
 * 的 {@code buildTypeHandlerMap()} 统一触发。
 *
 * @author wizard-lee
 */
public final class JsonTypeHandlerAutoConfigurer {

    /**
     * 构建 JSON 值对象 TypeHandler 并写入输出映射。
     *
     * @param serializer      JSON 序列化器
     * @param jdbcJsonValue   JDBC JSON 方言
     * @param voTypes         值对象类型清单
     * @param out             输出映射：javaType → handler
     */
    public static void register(JsonSerializer serializer,
                                JdbcJsonValue jdbcJsonValue,
                                Collection<Class<?>> voTypes,
                                Map<Class<?>, TypeHandler<?>> out) {
        for (Class<?> type : voTypes) {
            if (!IValueObject.class.isAssignableFrom(type)) {
                continue;                                  // 仅登记值对象
            }
            @SuppressWarnings("unchecked")
            Class<IValueObject> voType = (Class<IValueObject>) type;
            out.put(voType, new GenericJsonTypeHandler<>(voType, serializer, jdbcJsonValue));
        }
    }

    private JsonTypeHandlerAutoConfigurer() {
    }
}
