package io.pragmatic.ddd.afull.domain.order.service;

import io.pragmatic.ddd.afull.domain.order.model.Order;
import io.pragmatic.ddd.afull.domain.order.model.TotalPriceContext;
import io.pragmatic.ddd.base.IEntityPropertyCalculator;

import java.math.BigDecimal;

/**
 * 订单总价计算契约：根据订单项与用户 PIN 计算订单总价。
 *
 * @author wizard-lee
 */
public interface IOrderTotalPriceCalculator
        extends IEntityPropertyCalculator<TotalPriceContext, Order, BigDecimal> {
}
