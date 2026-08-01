package io.pragmatic.ddd.application;

import io.pragmatic.ddd.base.IEntityPropertyCalculator;

import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * IEntityPropertyResolver 适配器工厂：将同一个实体属性计算领域服务适配到不同场景的 Command DTO，一处定义多处复用。
 *
 * @author wizard-lee
 */
public final class EntityPropertyResolvers {

    private EntityPropertyResolvers() {
    }

    /** 取数需同时依赖 Command 与实体现状时使用。 */
    public static <C, E, T, R> IEntityPropertyResolver<C, E, R> of(
            IEntityPropertyCalculator<T, E, R> calculator,
            BiFunction<C, E, T> extractor) {
        return (command, entity) -> calculator.calculate(extractor.apply(command, entity), entity);
    }

    /** 取数仅依赖 Command 时使用。 */
    public static <C, E, T, R> IEntityPropertyResolver<C, E, R> of(
            IEntityPropertyCalculator<T, E, R> calculator,
            Function<C, T> extractor) {
        return (command, entity) -> calculator.calculate(extractor.apply(command), entity);
    }
}
