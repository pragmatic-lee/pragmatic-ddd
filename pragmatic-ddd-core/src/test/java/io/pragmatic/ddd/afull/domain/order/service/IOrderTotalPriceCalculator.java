package io.pragmatic.ddd.afull.domain.order.service;

import io.pragmatic.ddd.afull.domain.order.model.OrderItem;
import io.pragmatic.ddd.base.IDomainService;

import java.math.BigDecimal;
import java.util.List;

/**
 * 订单总价计算契约（类型转换领域服务）。
 *
 * @author wizard-lee
 */
public interface IOrderTotalPriceCalculator extends IDomainService {

    /**
     * 根据订单项列表计算订单总价。
     *
     * @param orderItemList 订单项列表
     * @param pin           用户 PIN
     * @return 订单总价
     */
    BigDecimal calculate(List<OrderItem> orderItemList, String pin);
}
