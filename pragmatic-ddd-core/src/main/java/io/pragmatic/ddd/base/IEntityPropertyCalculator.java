package io.pragmatic.ddd.base;

import io.pragmatic.ddd.service.IDomainService;

/**
 * 聚合派生属性计算领域服务契约。
 *
 * <p>业务意图：聚合根只持有业务状态、不内嵌计算逻辑（充血模型原则）。凡是由聚合内部状态推导而来的
 * "派生属性"（如订单总额 = Σ 订单项单价 × 数量、订单总数量、应付积分等），其计算职责必须外移到本契约，
 * 由领域层定义接口、应用层提供实现（{@link io.pragmatic.ddd.service.DomainServiceCategory#ATTRIBUTE_CALCULATOR}）。
 * 这样可保证同一派生属性的计算规则集中在单一领域服务中，避免散落在聚合构造器、业务方法或应用服务里，
 * 也便于被工厂在构造聚合前"先算后赋"。
 *
 * <p>约定：
 * <ul>
 *   <li>计算输入 {@code source} 必须是领域类型（值对象 / 基本类型 / 领域枚举 / 领域集合），不得为应用层 Command / DTO；
 *       若输入来自应用层入参，应在工厂内先将其转换为领域对象再传入。</li>
 *   <li>{@code entity} 为实体现状，创建场景（工厂建聚合前）下为 {@code null}，实现需兼容此情况。</li>
 *   <li>本契约只计算、不修改传入对象，是纯函数式的派生计算。</li>
 * </ul>
 *
 * @param <T> 计算输入类型（领域类型），不得为应用层 Command / DTO
 * @param <E> 实体类型
 * @param <R> 派生出的属性值类型
 * @author wizard-lee
 */
@FunctionalInterface
public interface IEntityPropertyCalculator<T, E, R> extends IDomainService {

    /**
     * 基于计算输入与实体现状，推导并返回聚合的某一派生属性值。
     *
     * @param source 计算输入，领域类型，不得为应用层 Command / DTO
     * @param entity 实体现状，创建场景下为 {@code null}
     * @return 计算得到的派生属性值
     */
    R calculate(T source, E entity);
}
