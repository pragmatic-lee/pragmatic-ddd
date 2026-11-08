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
     * 没有任何集合列声明时不注册：空标签表会把全局 {@code List.class} 槽位占掉，
     * 使其它聚合的集合列失去元素类型信息。
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
        if (columnListTypes.isEmpty()) {
            return;
        }
        out.put(List.class, new ListTypeHandler(
                serializer,
                jdbcJsonValue,
                columnListTypes,
                collections.converters()));
    }

    private ListTypeHandlerAutoConfigurer() {
    }
}
