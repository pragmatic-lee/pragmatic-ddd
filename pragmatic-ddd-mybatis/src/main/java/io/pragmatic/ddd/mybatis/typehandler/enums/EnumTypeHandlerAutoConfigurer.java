package io.pragmatic.ddd.mybatis.typehandler.enums;

import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.type.TypeHandlerRegistry;

import java.util.Collection;

/**
 * 手动注册器（无 Spring 依赖）：在 {@link SqlSessionFactory} 构建后批量注册枚举，
 * 并把 {@link UniversalEnumTypeHandler} 绑定进 MyBatis 的 {@code TypeHandlerRegistry}。
 * 对应设计文档 Step 10（提案 §5.3）。
 *
 * <p>调用方（非 Spring）在构建完 SqlSessionFactory 后调用一次即可；无码枚举也会被注册，
 * 其 rule 由 {@link EnumValueResolver#resolveRule(Class)} 回退默认（通常 CODE / ORDINAL / NAME）。
 */
public final class EnumTypeHandlerAutoConfigurer {

    /** 手动触发：批量注册枚举 + 绑定 UniversalEnumTypeHandler 到 TypeHandlerRegistry。 */
    public static void configure(EnumValueResolver resolver,
                                 SqlSessionFactory sqlSessionFactory,
                                 Collection<Class<?>> enumTypes) {
        resolver.registerAll(enumTypes);                       // 集中登记 + 预建索引
        TypeHandlerRegistry reg = sqlSessionFactory.getConfiguration().getTypeHandlerRegistry();
        for (Class<?> t : enumTypes) {
            if (!Enum.class.isAssignableFrom(t)) continue;
            registerHandler(reg, resolver, t);                 // 委托泛型辅助方法，统一类型变量 T
        }
    }

    /**
     * 泛型辅助：把枚举类与 handler 收敛到同一类型变量 {@code T}，
     * 使 {@code et}（{@code Class<T>}）与 handler（{@code TypeHandler<T>}）满足
     * MyBatis {@code <T> register(Class<T>, TypeHandler<? extends T>)} 的推断，规避通配符捕获间无法证明的 {@code <:} 关系。
     * 唯一 unchecked 收敛于此：运行时枚举具体类型不可知。
     */
    private static <T extends Enum<T>> void registerHandler(TypeHandlerRegistry reg,
                                                            EnumValueResolver resolver,
                                                            Class<?> t) {
        @SuppressWarnings("unchecked")
        Class<T> et = (Class<T>) t;                            // 唯一 unchecked：运行时枚举类型不可知
        EnumRule rule = resolver.resolveRule(et);             // 读 @EnumMapping 或默认
        UniversalEnumTypeHandler<T> handler = new UniversalEnumTypeHandler<>(et, rule, resolver);
        reg.register(et, handler);                             // 枚举类 ↔ handler 实例 对应，非 raw
    }
}
