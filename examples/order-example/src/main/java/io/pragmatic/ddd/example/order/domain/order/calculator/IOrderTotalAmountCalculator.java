package io.pragmatic.ddd.example.order.domain.order.calculator;

import io.pragmatic.ddd.base.IEntityPropertyCalculator;
import io.pragmatic.ddd.example.order.domain.order.model.Order;
import io.pragmatic.ddd.example.order.domain.order.model.OrderItem;
import io.pragmatic.ddd.example.order.domain.order.model.valueobject.Money;
import io.pragmatic.ddd.service.DomainService;
import io.pragmatic.ddd.service.DomainServiceCategory;

/**
 * 订单总额计算领域服务接口：声明"派生订单总额"这一业务意图。
 * 输入为领域 OrderItem 列表，输出领域 Money；不含实现与外部依赖，实现放在应用层。
 */
@DomainService(
        category = DomainServiceCategory.ATTRIBUTE_CALCULATOR,
        targetName = "Order/OrderItem",
        description = "汇总各订单项得到订单总额"
)
public interface IOrderTotalAmountCalculator extends IEntityPropertyCalculator<java.util.List<OrderItem>, Order, Money> {
}
