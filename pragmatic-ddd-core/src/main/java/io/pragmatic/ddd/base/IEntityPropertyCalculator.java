package io.pragmatic.ddd.base;

/**
 * 实体属性计算领域服务契约：基于计算输入与实体现状，判断并计算出实体某一属性的值。
 *
 * @param <T> 计算输入类型，必须是领域类型（值对象 / 基本类型 / 领域枚举），不得为应用层 Command/DTO
 * @param <E> 实体类型
 * @param <R> 计算得到的属性值类型
 * @author wizard-lee
 */
@FunctionalInterface
public interface IEntityPropertyCalculator<T, E, R> extends IDomainService {

    /**
     * 计算实体属性值。
     *
     * @param source 计算输入，领域类型，不得为应用层 Command/DTO
     * @param entity 实体现状，创建场景下为 null
     * @return 计算得到的属性值
     */
    R calculate(T source, E entity);
}
