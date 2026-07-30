package io.pragmatic.ddd.application;

import java.util.function.BiFunction;

/**
 * FieldResolver 适配器工厂：将同一个 FieldCalculator 适配到不同 Command DTO / 实体组合，一处定义多处复用。
 *
 * @author wizard-lee
 */
public final class FieldResolvers {

    private FieldResolvers() {}

    /** 从 Command DTO + 实体提取数据并计算，生成类型安全的 FieldResolver。 */
    public static <C, E, T, R> FieldResolver<C, E, R> from(
            Class<C> dtoType,
            Class<E> entityType,
            FieldCalculator<T, E, R> calculator,
            BiFunction<C, E, T> extractor) {
        return (command, entity) -> calculator.calculate(extractor.apply(command, entity), entity);
    }
}
