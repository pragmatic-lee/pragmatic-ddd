package io.pragmatic.ddd.example.order.domain.order.projection;

import io.pragmatic.ddd.repository.query.IAggregateProjection;

/**
 * 订单 ES 读模型视图形态契约，窄化框架通用投影接口为订单领域专属契约。
 *
 * @author wizard-lee
 */
public interface IOrderProjection extends IAggregateProjection {
}
