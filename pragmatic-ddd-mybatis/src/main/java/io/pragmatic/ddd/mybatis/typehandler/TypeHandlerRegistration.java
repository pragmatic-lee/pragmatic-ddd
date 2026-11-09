package io.pragmatic.ddd.mybatis.typehandler;

import org.apache.ibatis.type.TypeHandler;

/**
 * javaType 与 TypeHandler 的配对，不可变。装配侧导出产物（Context → Registry 方向），
 * 与声明侧输入单元 {@link CustomTypeHandlerMapping}（业务 → 装配器方向）分属两侧。
 *
 * @author wizard-lee
 */
public record TypeHandlerRegistration(Class<?> javaType, TypeHandler<?> handler) {
}
