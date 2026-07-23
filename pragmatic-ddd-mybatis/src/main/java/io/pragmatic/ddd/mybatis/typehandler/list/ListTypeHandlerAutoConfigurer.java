package io.pragmatic.ddd.mybatis.typehandler.list;

import io.pragmatic.ddd.mybatis.spi.JsonSerializer;
import io.pragmatic.ddd.mybatis.typehandler.TypeHandlerContext;
import io.pragmatic.ddd.mybatis.typehandler.json.JdbcJsonValue;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.type.TypeHandlerRegistry;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

/**
 * 把 {@link CollectionElementTypeConfig} 装配为单例 {@link ListTypeHandler} 并注册到 List.class。
 *
 * <p>注册入口与枚举/JSON 通道一致，由 {@link TypeHandlerContext#registerInto} 统一触发；
 * 无 Spring 依赖，原生 Java 亦可手动调用 {@link #configure}。
 */
public final class ListTypeHandlerAutoConfigurer {

    public static void configure(TypeHandlerContext ctx,
                                 SqlSessionFactory sqlSessionFactory,
                                 CollectionElementTypeConfig collections) {
        JsonSerializer serializer = ctx.serializer();
        JdbcJsonValue jdbcJsonValue = ctx.jdbcJsonValue();

        TypeHandlerRegistry reg = sqlSessionFactory.getConfiguration().getTypeHandlerRegistry();

        Map<String, Type> columnListTypes = collections.columnListTypes();
        Type defaultListType = columnListTypes.isEmpty() ? null
                : columnListTypes.values().iterator().next();

        ListTypeHandler handler = new ListTypeHandler(
                serializer, jdbcJsonValue, columnListTypes, collections.converters(), defaultListType);
        reg.register(List.class, handler);   // 单例注册到 List.class
    }
}
