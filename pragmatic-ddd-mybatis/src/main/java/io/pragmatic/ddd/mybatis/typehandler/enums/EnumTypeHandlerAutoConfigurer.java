package io.pragmatic.ddd.mybatis.typehandler.enums;

import io.pragmatic.ddd.mybatis.typehandler.TypeHandlerContext;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.type.TypeHandlerRegistry;

import java.util.Collection;
import java.util.Map;

/**
 * 手动注册器（无 Spring 依赖）：在 {@link SqlSessionFactory} 构建后批量注册枚举，
 * 并把 {@link UniversalEnumTypeHandler} 绑定进 MyBatis 的 {@code TypeHandlerRegistry}。
 * 对应设计文档 Step 10（提案 §5.3）。
 *
 * <p>调用方（非 Spring）在构建完 SqlSessionFactory 后调用一次即可；无码枚举也会被注册，
 * 其 rule 由 {@link EnumValueResolver} 的默认策略决定（通常 CODE / ORDINAL / NAME）。
 *
 * <p>策略不再通过枚举上的注解声明，而是在注册时按枚举显式设定——见
 * {@link #configure(EnumValueResolver, SqlSessionFactory, Map)}。
 */
public final class EnumTypeHandlerAutoConfigurer {

    /**
     * 主入口：按枚举指定策略注册。
     * {@code enumRules} 的 key 为枚举类，value 为该枚举的持久化策略 {@link EnumRule}。
     */
    public static void configure(EnumValueResolver resolver,
                                 SqlSessionFactory sqlSessionFactory,
                                 Map<Class<?>, EnumRule> enumRules) {
        resolver.registerAll(enumRules);                        // 集中登记 + 预建索引（按各自 rule）
        TypeHandlerRegistry reg = sqlSessionFactory.getConfiguration().getTypeHandlerRegistry();
        for (Map.Entry<Class<?>, EnumRule> e : enumRules.entrySet()) {
            Class<?> t = e.getKey();
            if (!Enum.class.isAssignableFrom(t)) continue;
            registerHandler(reg, resolver, t);                   // 委托泛型辅助方法，统一类型变量 T
        }
    }

    /**
     * 统一装配入口：从 {@link TypeHandlerContext} 取 resolver 与 enumRules，
     * 与 JSON 通道（{@code JsonTypeHandlerAutoConfigurer}）对称地消费同一上下文，
     * 确保枚举策略单点来源、杜绝双通道配置漂移。由 {@link TypeHandlerContext#registerInto} 调用。
     */
    public static void configure(TypeHandlerContext ctx, SqlSessionFactory sqlSessionFactory) {
        configure(ctx.resolver(), sqlSessionFactory, ctx.enumRules());
    }

    /** 便捷入口：批量注册枚举，统一使用 resolver 的默认策略。 */
    public static void configure(EnumValueResolver resolver,
                                 SqlSessionFactory sqlSessionFactory,
                                 Collection<Class<?>> enumTypes) {
        resolver.registerAll(enumTypes);
        TypeHandlerRegistry reg = sqlSessionFactory.getConfiguration().getTypeHandlerRegistry();
        for (Class<?> t : enumTypes) {
            if (!Enum.class.isAssignableFrom(t)) continue;
            registerHandler(reg, resolver, t);
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
        EnumRule rule = resolver.ruleOf(et);                   // 取该枚举注册时设定的策略
        UniversalEnumTypeHandler<T> handler = new UniversalEnumTypeHandler<>(et, rule, resolver);
        reg.register(et, handler);                             // 枚举类 ↔ handler 实例 对应，非 raw
    }
}
