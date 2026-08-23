package io.pragmatic.ddd.example.order.application.order;

import io.pragmatic.ddd.example.order.domain.order.model.Order;
import io.pragmatic.ddd.service.DomainService;
import io.pragmatic.ddd.service.DomainServiceCategory;
import io.pragmatic.ddd.service.IDomainService;

/**
 * 订单标识生成原子服务契约：生成订单及其子项的唯一标识。
 */
@DomainService(
        category = DomainServiceCategory.CAPABILITY_PROVIDER,
        targetName = "OrderId",
        description = "生成订单唯一标识"
)
public interface IOrderIdGenerator extends IDomainService {

    Long nextId();
}
