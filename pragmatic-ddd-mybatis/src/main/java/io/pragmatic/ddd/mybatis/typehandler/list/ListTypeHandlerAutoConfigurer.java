package io.pragmatic.ddd.mybatis.typehandler.list;

import io.pragmatic.ddd.mybatis.spi.JsonSerializer;
import io.pragmatic.ddd.mybatis.typehandler.TypeHandlerContext;
import io.pragmatic.ddd.mybatis.typehandler.json.JdbcJsonValue;
import org.apache.ibatis.type.TypeHandler;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

/**
 * List 通道构建器（无 Spring 依赖）：把 {@link CollectionElementTypeConfig} 装配为单例
 * {@link ListTypeHandler} 并登记到 {@code List.class}。
 *
 * <p>由 {@link TypeHandlerContext#registrations()} 的 {@code buildTypeHandlerMap()} 统一触发。
 *
 * @author wizard-lee
 */
public final class ListTypeHandlerAutoConfigurer {

    /**
     * 构建 List TypeHandler 并写入输出映射（注册到 {@code List.class}）。
     *
     * @param serializer     JSON 序列化器
     * @param jdbcJsonValue  JDBC JSON 方言
     * @param collections    集合元素类型配置
     * @param out            输出映射：javaType → handler
     */
    public static void register(JsonSerializer serializer,
                                JdbcJsonValue jdbcJsonValue,
                                CollectionElementTypeConfig collections,
                                Map<Class<?>, TypeHandler<?>> out) {
        Map<String, Type> columnListTypes = collections.columnListTypes();
        Type defaultListType = columnListTypes.isEmpty()
                ? null
                : columnListTypes.values().iterator().next();

        ListTypeHandler handler = new ListTypeHandler(
                serializer, jdbcJsonValue, columnListTypes, collections.converters(), defaultListType);
        out.put(List.class, handler);
    }

    private ListTypeHandlerAutoConfigurer() {
    }
}
