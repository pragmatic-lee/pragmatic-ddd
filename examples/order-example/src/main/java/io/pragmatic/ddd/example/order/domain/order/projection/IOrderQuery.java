package io.pragmatic.ddd.example.order.domain.order.projection;

import io.pragmatic.ddd.example.order.domain.order.projection.query.OrderListQuery;
import io.pragmatic.ddd.example.order.domain.order.projection.query.OrderOneQuery;
import io.pragmatic.ddd.example.order.domain.order.projection.query.OrderPageQuery;
import io.pragmatic.ddd.repository.query.IAggregateQuery;

/**
 * 订单读模型查询能力组合（CQRS 读侧，绕过聚合根）。
 *
 * <p>聚合 ID 类型为 {@code Long}；投影基类为 {@link IOrderProjection}（其下含
 * {@link OrderSummaryProjection} 概要投影与 {@code OrderEsProjection} 详情投影）；
 * 三个条件族分别继承框架分族父类 One / List / Page。</p>
 *
 * @author wizard-lee
 */
public interface IOrderQuery extends IAggregateQuery<
        Long, IOrderProjection, OrderOneQuery, OrderListQuery, OrderPageQuery> {

    /**
     * 按主键直取单个投影，显式指定取数来源的物理投影类型，再裁剪为目标子投影。
     * 用于在同一业务子投影下切换不同物理存储（ES / Redis 副本）。
     *
     * @param id 聚合主键
     * @param sourceProjection 取数来源的物理投影类型（如 {@code OrderEsProjection} / {@code OrderCacheProjection}）
     * @param projectionType 目标业务子投影类型
     * @param <X> 目标投影子类型
     * @return 命中的投影，未命中返回 null
     */
    <X extends IOrderProjection> X queryById(Object id, Class<?> sourceProjection, Class<X> projectionType);
}
