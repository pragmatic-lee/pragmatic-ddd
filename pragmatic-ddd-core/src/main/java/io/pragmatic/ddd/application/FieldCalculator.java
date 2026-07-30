package io.pragmatic.ddd.application;

/**
 * 字段计算器契约：从原始数据 + 实体中完成字段计算，与 DTO 解耦、可读实体、天然可复用。
 *
 * @author wizard-lee
 */
@FunctionalInterface
public interface FieldCalculator<T, E, R> {

    /** 执行字段计算。 */
    R calculate(T source, E entity);
}
