package io.pragmatic.ddd.application;

/**
 * 实体属性计算器契约：从原始数据 + 实体中完成实体属性计算，与 DTO 解耦、可读实体、天然可复用。
 *
 * @author wizard-lee
 */
@FunctionalInterface
public interface EntityPropertyCalculator<T, E, R> {

    /** 执行实体属性计算。 */
    R calculate(T source, E entity);
}
