package io.pragmatic.ddd.mybatis.typehandler;

import org.apache.ibatis.type.TypeHandler;

/**
 * javaType 与 TypeHandler 的配对，不可变。
 *
 * @author wizard-lee
 */
public record TypeHandlerRegistration(Class<?> javaType, TypeHandler<?> handler) {
}
