package io.pragmatic.ddd.example.order.domain.order.projection.materializer;

import io.pragmatic.ddd.example.order.domain.order.projection.OrderEsProjection;
import io.pragmatic.ddd.repository.query.IProjectionMaterializer;

/**
 * 订单 ES 读模型的物化契约，窄化框架通用物化接口为订单领域专属契约。
 *
 * @author wizard-lee
 */
public interface IOrderProjectionMaterializer
        extends IProjectionMaterializer<OrderEsProjection> {
}
