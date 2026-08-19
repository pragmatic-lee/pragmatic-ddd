package io.pragmatic.ddd.mybatis.typehandler.enums;

import io.pragmatic.ddd.mybatis.typehandler.TypeHandlerContext;
import org.apache.ibatis.type.TypeHandler;

import java.util.Map;

/**
 * 枚举通道构建器（无 Spring 依赖）：构建 {@link UniversalEnumTypeHandler} 并登记 javaType → handler。
 *
 * <p>由 {@link TypeHandlerContext#registrations()} 的 {@code buildTypeHandlerMap()} 统一触发，
 * 枚举策略单点来源，杜绝多通道配置漂移。
 *
 * @author wizard-lee
 */
public final class EnumTypeHandlerAutoConfigurer {

    /**
     * 构建枚举 TypeHandler 并写入输出映射。先集中登记枚举（预建索引），再逐个枚举构建 handler。
     *
     * @param resolver  枚举值解析器
     * @param enumRules 枚举类 → 持久化策略
     * @param out       输出映射：javaType → handler
     */
    public static void register(EnumValueResolver resolver,
                                Map<Class<?>, EnumRule> enumRules,
                                Map<Class<?>, TypeHandler<?>> out) {
        resolver.registerAll(enumRules);
        for (Map.Entry<Class<?>, EnumRule> entry : enumRules.entrySet()) {
            Class<?> type = entry.getKey();
            if (!Enum.class.isAssignableFrom(type)) {
                continue;
            }
            registerHandler(out, resolver, type);
        }
    }

    private static <T extends Enum<T>> void registerHandler(Map<Class<?>, TypeHandler<?>> out,
                                                            EnumValueResolver resolver,
                                                            Class<?> type) {
        @SuppressWarnings("unchecked")
        Class<T> enumType = (Class<T>) type;                 // 唯一 unchecked：运行时枚举类型不可知
        EnumRule rule = resolver.ruleOf(enumType);           // 取该枚举注册时设定的策略
        out.put(enumType, new UniversalEnumTypeHandler<>(enumType, rule, resolver));
    }

    private EnumTypeHandlerAutoConfigurer() {
    }
}
