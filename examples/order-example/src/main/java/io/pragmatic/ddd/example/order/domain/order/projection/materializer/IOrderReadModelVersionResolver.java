package io.pragmatic.ddd.example.order.domain.order.projection.materializer;

import io.pragmatic.ddd.repository.reconciliation.IReadModelVersionResolver;

/**
 * 订单 ES 读模型副本版本解析契约，窄化框架通用版本解析接口为订单领域专属契约。
 *
 * @author wizard-lee
 */
public interface IOrderReadModelVersionResolver
        extends IReadModelVersionResolver<Long> {
}
