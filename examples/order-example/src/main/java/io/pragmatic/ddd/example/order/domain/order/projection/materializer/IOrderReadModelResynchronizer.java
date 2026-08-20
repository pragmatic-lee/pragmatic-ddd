package io.pragmatic.ddd.example.order.domain.order.projection.materializer;

import io.pragmatic.ddd.repository.reconciliation.IReadModelResynchronizer;

/**
 * 订单 ES 读模型补同步契约，窄化框架通用补同步接口为订单领域专属契约。
 *
 * @author wizard-lee
 */
public interface IOrderReadModelResynchronizer
        extends IReadModelResynchronizer<Long> {
}
