package io.pragmatic.ddd.application;

import java.util.function.BiFunction;

/**
 * EntityPropertyResolver 适配器工厂：将同一个 EntityPropertyCalculator 适配到不同 Command DTO / 实体组合，一处定义多处复用。
 *
 * @author wizard-lee
 */
public final class EntityPropertyResolvers {

    private EntityPropertyResolvers() {}

    /** 从 Command DTO + 实体提取数据并计算，生成类型安全的 EntityPropertyResolver。 */
    public static <C, E, T, R> EntityPropertyResolver<C, E, R> from(
            Class<C> dtoType,
            Class<E> entityType,
            EntityPropertyCalculator<T, E, R> calculator,
            BiFunction<C, E, T> extractor) {
        return (command, entity) -> calculator.calculate(extractor.apply(command, entity), entity);
    }
}
