package io.pragmatic.ddd.afull.domain.order.service;

import io.pragmatic.ddd.base.IDomainService;

/**
 * 订单 ID 生成契约（类型转换领域服务）。
 *
 * @author wizard-lee
 */
public interface IOrderIdGenerator extends IDomainService {

    /**
     * 生成新的订单 ID。
     *
     * @return 订单 ID
     */
    long generate();
}
