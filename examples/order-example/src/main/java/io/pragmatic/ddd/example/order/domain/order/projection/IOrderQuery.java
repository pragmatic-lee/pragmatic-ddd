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
}
