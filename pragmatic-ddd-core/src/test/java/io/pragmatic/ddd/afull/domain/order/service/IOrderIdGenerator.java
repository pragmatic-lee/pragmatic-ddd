package io.pragmatic.ddd.afull.domain.order.service;

import io.pragmatic.ddd.service.DomainService;
import io.pragmatic.ddd.service.IDomainService;
import io.pragmatic.ddd.service.DomainServiceCategory;

/**
 * 订单 ID 生成契约（领域工厂 / 能力供给领域服务）。
 *
 * @author wizard-lee
 */
@DomainService(category = DomainServiceCategory.CAPABILITY_PROVIDER,
        targetName = "OrderId",
        description = "生成订单唯一标识，由应用层提供具体算法（雪花/序列）")
public interface IOrderIdGenerator extends IDomainService {

    /**
     * 生成新的订单 ID。
     *
     * @return 订单 ID
     */
    long generate();
}
